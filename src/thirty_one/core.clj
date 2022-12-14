(ns thirty-one.core
  (:require [thirty-one.io :as io]
            [thirty-one.gamestate :as gs]))

(defn game-loop
  "Loops the io-handler function until only 1 player remains"
  [gamestate io-handler]
  (loop [gamestate gamestate]
    (if (< (-> gamestate :players count) 2)
      (str (-> gamestate :players first :name) " wins!")
      (recur (io-handler gamestate)))))

(defn start-game 
  "Creates a new gamestate, adds all players, deals opening hands, and checks scores before moving to [[game-loop]]"
  [players & [io-handler]]
  {:pre (< (count players) 11)}
  (-> (reduce gs/add-player (gs/new-gamestate) players)
      (gs/deal)
      (game-loop (or io-handler io/default-io-handler))))

