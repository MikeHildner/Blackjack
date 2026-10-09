# How the strategy and odds are computed

This is the maths behind the Coach, the Chart and the Live odds panel. Nothing here
needs more than high-school probability.

## 1. The dealer has no choices

The dealer must hit below 17 and stand on 17 or more (optionally hitting a soft 17).
So given the up card, the dealer's final total is a fixed probability distribution
over {17, 18, 19, 20, 21, bust}.

`Odds.dealerDistribution` computes it by recursion over the dealer's hand state
(hard total, has-an-ace):

```
dist(hand):
    if total(hand) >= 17 and dealer must stand: 100% on that total
    if total(hand) > 21:                        100% bust
    otherwise: sum over each card value v of  P(next card = v) * dist(hand + v)
```

`P(next card = v)` comes from the actual remaining shoe composition (`Composition`),
so the numbers shift as cards are dealt. One simplification is made: the composition
is not depleted inside the recursion. For a six-deck shoe the error is a few
hundredths of a percent.

When the dealer shows a ten or an ace and has already peeked for blackjack without
finding one, the hole card is conditioned on *not* completing a blackjack. That is
why the panel shows a dealer ace busting about 17% of the time rather than the
unconditional 12%.

Typical fresh six-deck figures (dealer stands on soft 17):

| Up card | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | A |
|---------|---|---|---|---|---|---|---|---|----|---|
| Dealer busts | 35% | 37% | 40% | 43% | 42% | 26% | 24% | 23% | 23%* | 17%* |

\* conditioned on no blackjack.

## 2. Expected value of each action

Everything is measured per unit of the original bet.

- **Stand** with total T: `P(dealer busts) + P(dealer < T) − P(dealer > T)`.
- **Hit**: for each possible next card, either you bust (−1) or you are in a new
  state where you again choose the better of standing and hitting. This is a short
  recursion memoised on (hard total, has-an-ace).
- **Double**: exactly one card, then stand, with the result doubled.
- **Surrender**: always exactly −0.5.
- **Split** (approximate): each new hand gets one card and is then played out
  optimally without re-splitting; the result is doubled. It is marked "approx." in
  the UI because real splits allow re-splits and the two hands are not independent.

The Coach's chart and the EV calculator are independent implementations. The test
`basic strategy chart agrees with brute-force EV on hard totals` checks that every
hard-total cell in the chart is within 0.01 of the EV-maximising action.

## 3. Why the chart looks the way it does

Three facts explain most of basic strategy:

1. **Dealer 2–6 is weak.** The dealer busts 35–43% of the time. With a stiff hand
   (12–16) you are also likely to bust if you hit, so you stand and let the dealer
   take the risk. The exception is 12 against 2 or 3: only the four ten-values bust
   you, and the dealer busts less than 40%, so hitting is slightly better.
2. **Dealer 7–A is strong.** The dealer makes 17+ about three quarters of the time.
   Standing on 12–16 loses most of the time, so you hit even though you might bust.
3. **Get money down when you are the favourite.** Doubling 11 against anything but
   an ace, 10 against 2–9, 9 against 3–6; splitting aces and eights always.

Soft hands cannot bust on one card, so they hit and double more aggressively.

Surrender is right when every other option loses more than half a bet on average:
16 against 9, 10 or A, and 15 against 10.

## 4. Rule variations

| Rule | Effect on house edge | Strategy change |
|------|----------------------|-----------------|
| Dealer hits soft 17 | +0.22% | Double 11 vs A; double soft 18 vs 2; double soft 19 vs 6; surrender 15 vs A, 17 vs A, 8-8 vs A |
| No double after split | +0.14% | Do not split 2-2/3-3 vs 2-3, 4-4 vs 5-6, 6-6 vs 2 |
| Late surrender available | −0.08% | Surrender 16 vs 9/10/A, 15 vs 10 |
| 6:5 blackjack | +1.39% | None, it just costs you |
| Double on 10–11 only | +0.18% | Hit 9s and soft hands you would otherwise double |
| Single deck (vs 8) | −0.48% | Minor |

These are the standard published adjustments (see Wizard of Odds, Schlesinger's
*Blackjack Attack*). `Rules.houseEdgePercent` adds them up from a baseline of 0.43%
for an eight-deck S17 DAS game.

## 4a. The Oklahoma ante

Oklahoma tribal casinos charge a flat fee per hand, typically 50 cents on bets up to $50 and $1
above. It is paid when the bet is placed and never returned. Because it is flat, its cost as a
share of the bet is

    effective edge = rule edge + 100 × ante / bet

| Bet | Ante | Ante as % of bet | Total with a 0.6% rule edge |
|-----|------|------------------|-----------------------------|
| $5 | $0.50 | 10.0% | 10.6% |
| $10 | $0.50 | 5.0% | 5.6% |
| $25 | $0.50 | 2.0% | 2.6% |
| $50 | $0.50 | 1.0% | 1.6% |
| $100 | $1.00 | 1.0% | 1.6% |

`Rules.effectiveHouseEdgePercent(bet)` implements this. The ante also undermines card counting,
because a counter's small waiting bets are the ones the fee hits hardest.

A **continuous shuffling machine** (`Rules.continuousShuffle`) returns every card to the shoe
after each round. It does not change basic strategy or the rule edge, but it makes the running
count meaningless; the app resets the count every hand and says so.

## 4b. Free Bet Blackjack

Free Bet changes two things in the maths.

**Payoff profiles.** A hand has real money (`Hand.bet`) and house money (`Hand.freeBet`). A win
pays both, a loss costs only the real money, a push pays nothing. Every EV in `Odds` is computed
for the hand's profile and divided by its wager so actions stay comparable:

    EV(stand) = [ P(win) × (bet + free) − P(lose) × bet ] / (bet + free)

For a classic hand (`free = 0`) this is the usual `P(win) − P(lose)`. For the second hand of a
free split (`bet = 0`) it is just `P(win)`: a push is worth exactly as much as a loss, which is
why such hands play more aggressively.

A free double adds `wager` to `free`; a real double adds it to `bet` and risks it. That is the
whole difference, and it is why the free double on 9, 10 and 11 is always right.

**Push 22.** `DealerDistribution` keeps totals 17..26 separately. Under Free Bet rules a 22
counts as neither a win nor a loss for the player, so `pPlayerWins` leaves it out. The dealer
makes exactly 22 about 8% of the time against a 6, which is why standing on stiffs is worth a
little less than in the classic game.

**Strategy.** There is no hand-coded Free Bet chart. `FreeBetStrategy` asks `Odds` for every
legal action against a fresh shoe and takes the best, for both the real-money and the free-bet
profile, and memoises the answers. The published house edge of 1.04% (6D, H17, DAS, RSA, 3:2,
no surrender) and its rule deltas are in `Rules.freeBetHouseEdge`.

## 5. Card counting

Hi-Lo tags 2–6 as +1, 7–9 as 0, tens and aces as −1. A deck sums to zero, so a
positive running count means the undealt cards are richer in tens and aces than
average. That helps the player because:

- blackjacks become more frequent, and the player's pay 3:2 while the dealer's pay 1:1;
- doubles on 10 and 11 succeed more often;
- the dealer, who must hit stiffs, busts more often;
- insurance becomes a positive bet when tens exceed a third of the shoe (true count ≈ +3).

The true count divides the running count by the decks remaining. Each point is
worth about +0.5% to the player. The suggested bet ramp in the app is
`(true count − 1)` units, between 1 and 8.
