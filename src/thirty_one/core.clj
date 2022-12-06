(ns thirty-one.core
  (:require [thirty-one.io :as io]
            [thirty-one.gamestate :as gs]))

(defn game-loop
  [gamestate input-handler]
  (loop [gamestate gamestate]
    (if (< (-> gamestate :players count) 2)
      (str (-> gamestate :players first :name) " wins!")
      (recur (input-handler gamestate)))))

(defn start-game 
  "Creates a new gamestate, adds all players, deals opening hands, and checks scores before moving to main gameplay loop"
  [players & [input-handler]]
  (-> (reduce gs/add-player (gs/new-gamestate) players)
      (gs/deal)
      (game-loop (or input-handler io/default-input-handler))))

          
  

