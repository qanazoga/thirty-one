(ns thirty-one.gamestate
  "Builds and edits the gamestate"
  (:require [thirty-one.deck :as deck]
            [thirty-one.evaluator :as ev]))

(defn new-gamestate
  "Creates the basic skeleton of the gamestate, including a shuffled deck"
  []
  {:active-player 0
   :awaiting nil
   :deck (deck/build-deck)
   :discard nil
   :round 0
   :knocking-player nil
   :players []})


(defn dec-life
  "Subtracts a life from the player at `player-index`"
  [gamestate player-index]
  (update-in gamestate [:players player-index :lives] dec))

(defn score
  "Removes a point from any player for whom [[ev/player-loses-life?]] is `true`"
  [gamestate]
  (loop [gs (assoc gamestate :awaiting :end-round)
         losers (ev/losing-indexes gamestate)]
    (if (empty? losers)
      gs
      (recur (dec-life gs (first losers))
             (rest losers)))))

(defn add-player
  "Adds a player to the gamestate"
  [gamestate player-name]
  (-> gamestate
      (update-in [:players] 
                 #(conj % {:name player-name
                           :hand []
                           :hand-points 0
                           :lives 5}))))

(defn next-player
  "Updates `:active-player` to the next index (or 0 if at end)"
  [gamestate]
  (if (= (gamestate :active-player) 
         (dec (count (gamestate :players))))
    0
    (inc (gamestate :active-player))))

  
(defn update-hand-points
  "Updates the player at `player-index`'s `:hand-points`"
  [gamestate player-index]
  (let [hand (-> gamestate :players (get player-index) :hand)
        suits [:clubs :diamonds :hearts :spades]
        max-points (apply max (for [suit suits]
                               (->> hand
                                    (filter #(= suit (:suit %)))
                                    (map :value)
                                    (apply +))))]
    (-> gamestate
        (assoc-in [:players player-index :hand-points]
                  max-points))))

(defn update-all-hand-points
  "Updates `:hand-points` for all players"
  ([gamestate] (update-all-hand-points gamestate 0))
  ([gamestate player-index]
   (if (= player-index (-> gamestate :players count))
     gamestate
     (recur (update-hand-points gamestate player-index) 
            (inc player-index)))))

(defn draw-from-deck
  "Adds the first card in the `:deck` to the hand of the player at `player-index`"
  [gamestate player-index]
  (let [deck (gamestate :deck)
        hand (-> gamestate :players (get player-index) :hand)]
    (-> gamestate
        (assoc-in [:players player-index :hand] 
                  (conj hand (first deck)))
        (assoc :deck (rest deck))
        (assoc :awaiting :discard))))

(defn draw-from-discard
  "Adds the card from `:discard` to the hand of the player at `player-index`"
  [gamestate player-index]
  (let [card (-> gamestate :discard)]
    (-> gamestate
        (assoc :discard nil)
        (update-in [:players player-index :hand] 
                   #(conj % card))
        (assoc :awaiting :discard))))

(defn discard
  "Removes the card at `card-index` from the hand of the player at `player-index`, sets the `gamestate`'s `:discard` to that card"
  [gamestate player-index card-index]
  (let [hand (-> gamestate :players (get player-index) :hand)
        card (hand card-index)
        gs (-> gamestate
               (assoc :discard card)
               (assoc-in [:players player-index :hand] 
                         (vec (remove #{card} hand)))
               (update-hand-points player-index)
               (assoc :active-player (next-player gamestate)))]
    (if (ev/time-to-score? gs)
      (score gs)
      (assoc gs :awaiting :start-turn))))

(defn knock
  "Sets the `gamestate`'s `:knocking-player` to `player-index`"
  [gamestate player-index]
  {:pre [(nil? (gamestate :knocking-player))]}
  (-> gamestate
      (assoc :knocking-player player-index)
      (assoc :active-player (next-player gamestate))
      (assoc :awaiting :start-turn)))
  
(defn deal
  "Deals 3 cards to each player, then one to `:discard`"
  [gamestate]
  (loop [gs gamestate
         deal-order (->> gamestate :players count range (repeat 3) flatten)]
    (if (empty? deal-order)
      (-> gs
          (update-all-hand-points)
          (assoc-in [:discard] (-> gs :deck first))
          (assoc-in [:deck] (-> gs :deck rest))
          (assoc-in [:awaiting] :start-turn))
      (recur (draw-from-deck gs (first deal-order))
             (rest deal-order)))))

(defn empty-hands
  "Removes all cards from each `:player`'s hand"
  ([gamestate] (empty-hands gamestate 0))
  ([gamestate player-index]
   (if (= (-> gamestate :players count)
          player-index)
     gamestate
     (recur (assoc-in gamestate [:players player-index :hand] []) 
            (inc player-index)))))
  

(defn new-round
  "Shuffles a new deck, removes players who are no longer in the game, then deals"
  [gamestate]
  ;; (deal) sets the :awaiting and :discard, we don't need to change them here.
  (as-> gamestate gs
      (assoc gs :deck (deck/build-deck))
      (assoc gs :knocking-player nil)
      (assoc-in gs [:players]
                (->> (gs :players)
                     (remove #(zero? (% :lives))) 
                     vec))
      (update gs :round inc)
      (empty-hands gs)
      (deal gs)
      (assoc gs :active-player (mod (gs :round) (count (gs :players))))))

