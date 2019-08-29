package net.sf.cpsolver.itc.heuristics.search;

import java.text.DecimalFormat;
import java.util.*;

import net.sf.cpsolver.ifs.heuristics.NeighbourSelection;
import net.sf.cpsolver.ifs.model.Neighbour;
import net.sf.cpsolver.ifs.model.Value;
import net.sf.cpsolver.ifs.model.Variable;
import net.sf.cpsolver.ifs.solution.Solution;
import net.sf.cpsolver.ifs.solution.SolutionListener;
import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.itc.heuristics.neighbour.ItcLazyNeighbour;
import net.sf.cpsolver.itc.heuristics.neighbour.ItcLazyNeighbour.LazyNeighbourAcceptanceCriterion;
import net.sf.cpsolver.itc.heuristics.search.ItcHillClimber.NeighbourSelector;

import org.apache.log4j.Logger;

/**
 * Hill climber algorithm. Any move that decreases the overall solution value is rejected.
 * <br><br>
 * The search is stopped ({@link ItcHillClimberSeq#selectNeighbour(Solution)} returns null) after
 * HillClimber.MaxIdle idle (not improving) iterations.
 * <br><br>
 * Custom neighbours can be set using HillClimber.Neighbours property that should
 * contain semicolon separated list of {@link NeighbourSelection}. By default, 
 * each neighbour selection is selected with the same probability (each has 1 point in
 * a roulette wheel selection). It can be changed by adding &nbsp;@n at the end
 * of the name of the class, for example:<br>
 * <code>
 * HillClimber.Neighbours=net.sf.cpsolver.itc.tim.neighbours.TimRoomMove;net.sf.cpsolver.itc.tim.neighbours.TimTimeMove;net.sf.cpsolver.itc.tim.neighbours.TimSwapMove;net.sf.cpsolver.itc.heuristics.neighbour.selection.ItcSwapMove;net.sf.cpsolver.itc.tim.neighbours.TimPrecedenceMove@0.1
 * </code>
 * <br>
 * Selector TimPrecedenceMove is 10&times; less probable to be selected than other selectors.
 * When SimulatedAnnealing.Random is true, all selectors are selected with the same probability, ignoring these weights.
 * <br><br>
 * When HillClimber.Update is true, {@link NeighbourSelector#update(Neighbour, long)} is called 
 * after each iteration (on the selector that was used) and roulette wheel selection 
 * that is using {@link NeighbourSelector#getPoints()} is used to pick a selector in each iteration. 
 * See {@link NeighbourSelector} for more details. 
 * 
 *  
 * @version
 * ITC2007 1.0<br>
 * Copyright (C) 2007 Tomas Muller<br>
 * <a href="mailto:muller@unitime.org">muller@unitime.org</a><br>
 * <a href="http://muller.unitime.org">http://muller.unitime.org</a><br>
 * <br>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * <br><br>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <br><br>
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not see
 * <a href='http://www.gnu.org/licenses/'>http://www.gnu.org/licenses/</a>.
 */
public class ItcHillClimberSeq<V extends Variable<V, T>, T extends Value<V, T>> implements NeighbourSelection<V,T>, SolutionListener<V,T>, LazyNeighbourAcceptanceCriterion<V,T> {
    private static Logger sLog = Logger.getLogger(ItcHillClimberSeq.class);
    private static DecimalFormat sDF2 = new DecimalFormat("0.00");
    private static boolean sInfo = sLog.isInfoEnabled();
    private int iMaxIdleIters = 100000;
    private int iLastImprovingIter = 0;
    private int iIter = 0;

    private long iT0 = -1;
    private HeuristicSequence heuristicSequence;
    private ArrayList<NeighbourSelector<V, T>> previous;
    private int numNullNeighbour = 0;
    private int iResetPreviousIter = -1;
    private int iLearing = 2;
    
    /**
     * Constructor
     * <ul>
     * <li>HillClimber.MaxIdle ... maximum number of idle iterations (default is 200000)
     * <li>HillClimber.Neighbours ... semicolon separated list of classes implementing {@link NeighbourSelection}
     * <li>HillClimber.Random ... when true, a neighbour selector is selected randomly
     * <li>HillClimber.Update ... when true, a neighbour selector is selected using {@link NeighbourSelector#getPoints()} weights (roulette wheel selection)
     * </ul>
     */

    public ItcHillClimberSeq(DataProperties properties) throws Exception {
        iMaxIdleIters = properties.getPropertyInt("HillClimber.MaxIdle", iMaxIdleIters);
        iResetPreviousIter = properties.getPropertyInt("Sequence.MaxSequenceResetIter", iResetPreviousIter);
        iLearing = properties.getPropertyInt("Sequence.LearningMethod", iLearing);
        heuristicSequence = new HeuristicSequence(properties, 0);
        previous = null;
    }
    
    /** Initialization */
    public void init(Solver<V,T> solver) {
        solver.currentSolution().addSolutionListener(this);
        heuristicSequence.init(solver);
    }

    /**
     * Select one of the given neighbourhoods
     * select neighbour, return it if its value is below or equal to zero (continue with the next selection otherwise).
     * Return null when the given number of idle iterations is reached.
     */
    public Neighbour<V,T> selectNeighbour(Solution<V,T> solution) {

        while (true){
            iIter ++;
            if (iIter-iLastImprovingIter>=iMaxIdleIters) break;

            // Select neighbour
            NeighbourSelector<V, T> selector = heuristicSequence.getNeighbour(previous, solution.getTime());
            Neighbour<V,T> n = selector.selectNeighbour(solution);

            if (n!=null) {
                if (n instanceof ItcLazyNeighbour) { // If lazy, return the change and accept func is used
                    ((ItcLazyNeighbour<V,T>)n).setAcceptanceCriterion(this);
                    return n;
                } else if (n.value()<=0.0) { // Return change if its better
                    // Create new sequence if there isnt one
                    if(previous == null){
                        previous = new ArrayList<>();
                    }
                    // add llh to sequence
                    previous.add(selector);

                    // Update scores depending on the learning method
                    if(previous.size() == 1){
                        if(iLearing == 0){
                            heuristicSequence.updateScore(null, previous.get(previous.size()-1), heuristicSequence.isEnded());
                        }else if(iLearing == 1){
                            heuristicSequence.updateScoreNL(null, previous.get(previous.size()-1), heuristicSequence.isEnded(), solution.getTime());
                        }else if(iLearing == 2){
                            heuristicSequence.updateScoreDelta(null, previous.get(previous.size()-1), heuristicSequence.isEnded(), -n.value());
                        }
                    }else{
                        if(iLearing == 0){
                            heuristicSequence.updateScore(previous.get(previous.size()-2), previous.get(previous.size()-1), heuristicSequence.isEnded());
                        }else if(iLearing == 1){
                            heuristicSequence.updateScoreNL(previous.get(previous.size()-2), previous.get(previous.size()-1), heuristicSequence.isEnded(), solution.getTime());
                        }else if(iLearing == 2){
                            heuristicSequence.updateScoreDelta(previous.get(previous.size()-2), previous.get(previous.size()-1), heuristicSequence.isEnded(), -n.value());
                        }
                    }

                    // If the sequence is ended remove it
                    if(heuristicSequence.isEnded()){
                        previous = null;
                    }

                    return n;
                }else{
                    numNullNeighbour++;
                    // Reset sequence
                    if(numNullNeighbour % iResetPreviousIter == 0 && numNullNeighbour != 0){
                        previous = null;
                        //System.out.println("Previous set to null");
                    }
                }
            }
        }
        iIter = 0; iLastImprovingIter = 0; iT0 = -1;
        return null;
    }


    /** Implementation of {@link net.sf.cpsolver.itc.heuristics.neighbour.ItcLazyNeighbour.LazyNeighbourAcceptanceCriterion} interface */
    public boolean accept(ItcLazyNeighbour<V,T> neighbour, double value) {
        return value<=0;
    }

    /**
     * Memorize the iteration when the last best solution was found.
     */
    public void bestSaved(Solution<V,T> solution) {
        iLastImprovingIter = iIter;
    }
    public void solutionUpdated(Solution<V,T> solution) {}
    public void getInfo(Solution<V,T> solution, Map<String, String> info) {}
    public void getInfo(Solution<V,T> solution, Map<String, String> info, Collection<V> variables) {}
    public void bestCleared(Solution<V,T> solution) {}
    public void bestRestored(Solution<V,T> solution){}  
    
    public static interface HillClimberSelection {
        public void setHcMode(boolean hcMode);
    }
}
