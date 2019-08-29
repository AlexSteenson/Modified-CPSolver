package net.sf.cpsolver.itc.heuristics.search;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import net.sf.cpsolver.ifs.extension.Extension;
import net.sf.cpsolver.ifs.heuristics.NeighbourSelection;
import net.sf.cpsolver.ifs.model.Neighbour;
import net.sf.cpsolver.ifs.model.Value;
import net.sf.cpsolver.ifs.model.Variable;
import net.sf.cpsolver.ifs.solution.Solution;
import net.sf.cpsolver.ifs.solution.SolutionListener;
import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.itc.heuristics.ItcParameterWeightOscillation;
import net.sf.cpsolver.itc.heuristics.ItcParameterWeightOscillation.OscillationListener;
import net.sf.cpsolver.itc.heuristics.neighbour.ItcLazyNeighbour;
import net.sf.cpsolver.itc.heuristics.neighbour.ItcLazyNeighbour.LazyNeighbourAcceptanceCriterion;
import net.sf.cpsolver.itc.heuristics.search.ItcHillClimber.NeighbourSelector;

/**
 * Great deluge algorithm. A move is accepted if the
 * overall solution value of the new solution will not exceed
 * given bound.
 * <br><br>
 * Bound is initialized to value GreatDeluge.UpperBoundRate &times; value
 * of the best solution. Bound is decreased after each iteration,
 * it is multiplied by GreatDeluge.CoolRate (alternatively, GreatDeluge.CoolRateInv can be
 * defined, which is GreatDeluge.CoolRate = 1 - (1 / GreatDeluge.CoolRateInv ) ). When
 * a limit GreatDeluge.LowerBoundRate &times; value of the best solution
 * is reached, the bound is increased back to GreatDeluge.UpperBoundRate &times
 * value of the best solution.
 * <br><br>
 * If there was no improvement found between the increments of the bound, the new bound is changed to
 * GreatDeluge.UpperBoundRate^2 with the lower limit set to GreatDeluge.LowerBoundRate^2,
 * GreatDeluge.UpperBoundRate^3 and GreatDeluge.LowerBoundRate^3, etc. till there is an
 * improvement found.
 * <br><br>
 * Custom neighbours can be set using GreatDeluge.Neighbours property that should
 * contain semicolon separated list of {@link NeighbourSelection}. By default,
 * each neighbour selection is selected with the same probability (each has 1 point in
 * a roulette wheel selection). It can be changed by adding &nbsp;@n at the end
 * of the name of the class, for example:<br>
 * <code>
 * GreatDeluge.Neighbours=net.sf.cpsolver.itc.tim.neighbours.TimRoomMove;net.sf.cpsolver.itc.tim.neighbours.TimTimeMove;net.sf.cpsolver.itc.tim.neighbours.TimSwapMove;net.sf.cpsolver.itc.heuristics.neighbour.selection.ItcSwapMove;net.sf.cpsolver.itc.tim.neighbours.TimPrecedenceMove@0.1
 * </code>
 * <br>
 * Selector TimPrecedenceMove is 10&times; less probable to be selected than other selectors.
 * When GreatDeluge.Random is true, all selectors are selected with the same probability, ignoring these weights.
 * <br><br>
 * When Itc.NextHeuristicsOnReheat parameter is true, a chance is given to another
 * search strategy by returning once null in {@link ItcGreatDelugeSeq#selectNeighbour(Solution)}.
 * When Itc.NextHeuristicsOnReheat.AlterBound is also true, the bound is altered after the null
 * is returned (so that an improvement of the best solution made by some other search
 * strategy is considered as well).
 * <br><br>
 * When GreatDeluge.Update is true, {@link NeighbourSelector#update(Neighbour, long)} is called
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
public class ItcGreatDelugeSeq<V extends Variable<V, T>, T extends Value<V, T>> implements NeighbourSelection<V,T>, SolutionListener<V,T>, LazyNeighbourAcceptanceCriterion<V,T>, OscillationListener {
    private static Logger sLog = Logger.getLogger(ItcGreatDelugeSeq.class);
    private static boolean sInfo = sLog.isInfoEnabled();
    private static DecimalFormat sDF2 = new DecimalFormat("0.00");
    private static DecimalFormat sDF5 = new DecimalFormat("0.00000");
    private double iBound = 0.0;
    private double iCoolRate = 0.9999995;
    private long iIter;
    private double iUpperBoundRate = 1.05;
    private double iLowerBoundRate = 0.97;
    private int iMoves = 0;
    private int iAcceptedMoves = 0;
    private int iNrIdle = 0;
    private long iT0 = -1;
    private long iLastImprovingIter = 0;
    private boolean iNextHeuristicsOnReheat = false;
    private int numNullNeighbour = 0;
    private int iLearing = 2;

    private List<NeighbourSelector<V,T>> iNeighbourSelectors = new ArrayList<NeighbourSelector<V,T>>();
    private boolean iRandomSelection = false;
    private boolean iUpdatePoints = false;
    private double iTotalBonus;
    private boolean iAlterBound = false;
    private boolean iAlterBoundOnReheat = false;
    private int iResetPreviousIter = -1;

    private HeuristicSequence heuristicSequence;
    private ArrayList<NeighbourSelector<V, T>> previous;
    private NeighbourSelector<V, T> selector;
    private boolean coolInAccept = false;

    private int reset = 0;
    private int prevNull = 0;

    /**
     * Constructor. Following problem properties are considered:
     * <ul>
     * <li>Sequence.LearningMethod ... The learning method to be used when updating the sequence scores
     * <li>GreatDeluge.CoolInAccept ... when to cool, if true its when a solution is accepted
     * <li>GreatDeluge.CoolRate ... bound cooling rate (default 0.9999995)
     * <li>GreatDeluge.CoolRateInv ... inverse cooling rate (i.e., GreatDeluge.CoolRate = 1 - (1 / GreatDeluge.CoolRateInv ) )s
     * <li>GreatDeluge.UpperBoundRate ... bound upper bound limit relative to best solution ever found (default 1.05)
     * <li>GreatDeluge.LowerBoundRate ... bound lower bound limit relative to best solution ever found (default 0.97)
     * <li>GreatDeluge.Neighbours ... semicolon separated list of classes implementing {@link NeighbourSelection}
     * <li>GreatDeluge.Random ... when true, a neighbour selector is selected randomly
     * <li>GreatDeluge.Update ... when true, a neighbour selector is selected using {@link NeighbourSelector#getPoints()} weights (roulette wheel selection)
     * <li>Itc.NextHeuristicsOnReheat ... when true, null is returned in {@link ItcGreatDelugeSeq#selectNeighbour(Solution)} once just after a reheat
     * <li>Itc.NextHeuristicsOnReheat.AlterBound ... when true, bound is updated after null is returned
     * </ul>
     * @param properties problem properties
     */    public ItcGreatDelugeSeq(DataProperties properties) throws Exception {
        iCoolRate = properties.getPropertyDouble("GreatDeluge.CoolRate", iCoolRate);
        if (properties.getProperty("GreatDeluge.CoolRateInv")!=null) {
            iCoolRate = 1.0 - (1.0 / properties.getPropertyDouble("GreatDeluge.CoolRateInv", 1.0 / (1.0 - iCoolRate)));
            sLog.info("Cool rate is "+iCoolRate+" (inv:"+properties.getProperty("GreatDeluge.CoolRateInv")+")");
        }
        iUpperBoundRate = properties.getPropertyDouble("GreatDeluge.UpperBoundRate", iUpperBoundRate);
        //iLowerBoundRate = readValue();
        iLowerBoundRate = properties.getPropertyDouble("GreatDeluge.LowerBoundRate", iLowerBoundRate);
        iNextHeuristicsOnReheat = properties.getPropertyBoolean("Itc.NextHeuristicsOnReheat", iNextHeuristicsOnReheat);
        iAlterBoundOnReheat = properties.getPropertyBoolean("Itc.NextHeuristicsOnReheat.AlterBound", iAlterBoundOnReheat);
        iResetPreviousIter = properties.getPropertyInt("Sequence.MaxSequenceResetIter", iResetPreviousIter);
        coolInAccept = properties.getPropertyBoolean("General.CoolInAccept", coolInAccept);
        iLearing = properties.getPropertyInt("Sequence.LearningMethod", iLearing);
        heuristicSequence = new HeuristicSequence(properties, 1);
        previous = null;
    }

    /**
     * This is only used when tuning a parameter. It reads the parameters value from a file and uses it
     * @return
     */
    public double readValue(){
        // The name of the file to open.
        String fileName = "value.txt";

        // This will reference one line at a time
        String line = null;

        try {
            FileReader fileReader =
                    new FileReader(fileName);

            BufferedReader bufferedReader =
                    new BufferedReader(fileReader);

            while((line = bufferedReader.readLine()) != null) {
                return Double.parseDouble(line);
            }

            // Close files.
            bufferedReader.close();
        }
        catch(FileNotFoundException ex) {
            System.out.println(
                    "Unable to open file '" +
                            fileName + "'");
        }
        catch(IOException ex) {
            System.out.println(
                    "Error reading file '"
                            + fileName + "'");
        }
        return 0;
    }

    /** Initialization */
    public void init(Solver<V,T> solver) {
        iIter = -1;
        solver.currentSolution().addSolutionListener(this);
        for (Extension<V, T> ext: solver.getExtensions()) {
            if (ext instanceof ItcParameterWeightOscillation)
                ((ItcParameterWeightOscillation<V,T>)ext).addOscillationListener(this);
        }
        iTotalBonus = 0;
        for (NeighbourSelector<V,T> s: iNeighbourSelectors) {
            s.init(solver);
            iTotalBonus += s.getBonus();
        }

        heuristicSequence.init(solver);
    }

    /**
     * Generates a neighbour
     * @param solution
     * @return
     */
    private Neighbour<V,T> genMove(Solution<V,T> solution) {
        while (true) {
            if (incIter(solution)) {
                iAlterBound = iAlterBoundOnReheat;
                return null;
            }
            if (iAlterBound) {
                iBound = Math.max(solution.getBestValue()+2.0, Math.pow(iUpperBoundRate,Math.max(1,iNrIdle)) * solution.getBestValue());
                iAlterBound = false;
            }
            // Get llh
            selector = heuristicSequence.getNeighbour(previous, solution.getTime());
            // Get neighbour
            Neighbour<V,T> n = selector.selectNeighbour(solution);

            if (n != null) return n;
            numNullNeighbour++;
            // If its null for too long, reset the sequence
            if(numNullNeighbour % iResetPreviousIter == 0 && numNullNeighbour != 0){
                previous = null;
                prevNull++;
                //System.out.println("Previous set to null gen");
            }
//            if(numNullNeighbour % iResetPreviousIter * 10 == 0 && numNullNeighbour != 0){
//                previous = null;
//                heuristicSequence.resetScores();
//                reset++;
//                //System.out.println("Previous set to null gen");
//            }
        }
    }

    public boolean accept(Solution<V,T> solution, Neighbour<V,T> neighbour) {
        if (neighbour instanceof ItcLazyNeighbour) {
            ((ItcLazyNeighbour<V,T>)neighbour).setAcceptanceCriterion(this);
            return true;
        } else return (neighbour.value()<=0 || solution.getModel().getTotalValue()+neighbour.value()<=iBound);
    }

    /** Implementation of {@link net.sf.cpsolver.itc.heuristics.neighbour.ItcLazyNeighbour.LazyNeighbourAcceptanceCriterion} interface */
    public boolean accept(ItcLazyNeighbour<V,T> neighbour, double value) {
        return (value<=0 || neighbour.getModel().getTotalValue()<=iBound);
    }

    // Cools the bound value
    public void coolBound(Solution<V,T> solution){
        if (iIter<0) {
            iIter = 0; iLastImprovingIter = 0;
            iT0 = System.currentTimeMillis();
            iBound = iUpperBoundRate * solution.getBestValue();
        } else {
            iIter++; iBound *= iCoolRate;
        }
    }

    public boolean incIter(Solution<V,T> solution) {
        if(!coolInAccept)
            coolBound(solution);
        if (iBound<Math.pow(iLowerBoundRate,1+iNrIdle)*solution.getBestValue()) {
            iNrIdle++;
            sLog.info(" -<["+iNrIdle+"]>- ");
            iBound = Math.max(solution.getBestValue()+2.0, Math.pow(iUpperBoundRate,iNrIdle) * solution.getBestValue());
            return iNextHeuristicsOnReheat;
        }
        return false;
    }

    /** Neighbour selection */
    public Neighbour<V,T> selectNeighbour(Solution<V,T> solution) {
        Neighbour<V,T> neighbour = null;
        while ((neighbour=genMove(solution))!=null) {
            iMoves++;
            // If the neighbour is accepted
            if (accept(solution,neighbour)) {
                //writeSolutionValueToFile(solution.getModel().getTotalValue(), solution.getIteration());
                iAcceptedMoves++;
                // If its an improving move
                if(neighbour.value() <= 0){
                    // Cool bounds
                    if(coolInAccept)
                        coolBound(solution);
                    //System.out.println("Value is " + solution.getBestValue());
                    // Create new sequence if there isn't one
                    if(previous == null){
                        previous = new ArrayList<>();
                    }
                    // Add llh to sequence
                    previous.add(selector);

                    // Update scores depending on learning method
                    if(previous.size() == 1){
                        if(iLearing == 0){
                            heuristicSequence.updateScore(null, previous.get(previous.size()-1), heuristicSequence.isEnded());
                        }else if(iLearing == 1){
                            heuristicSequence.updateScoreNL(null, previous.get(previous.size()-1), heuristicSequence.isEnded(), solution.getTime());
                        }else if(iLearing == 2){
                            heuristicSequence.updateScoreDelta(null, previous.get(previous.size()-1), heuristicSequence.isEnded(), -neighbour.value());
                        }
                    }else {
                        if (iLearing == 0) {
                            heuristicSequence.updateScore(previous.get(previous.size() - 2), previous.get(previous.size() - 1), heuristicSequence.isEnded());
                        } else if (iLearing == 1) {
                            heuristicSequence.updateScoreNL(previous.get(previous.size() - 2), previous.get(previous.size() - 1), heuristicSequence.isEnded(), solution.getTime());
                        } else if (iLearing == 2) {
                            heuristicSequence.updateScoreDelta(previous.get(previous.size() - 2), previous.get(previous.size() - 1), heuristicSequence.isEnded(), -neighbour.value());
                        }
                    }
                    // If the sequence is ended remove the sequence
                    if(heuristicSequence.isEnded()){
                        previous = null;
                    }
                }
                break;
            }else{
//                if(previous != null){
//                    heuristicSequence.decScore(previous.get(previous.size()-1), selector, heuristicSequence.isEnded());
//                }else{
//                    heuristicSequence.decScore(null, selector, heuristicSequence.isEnded());
//                }

                // Logging
                numNullNeighbour++;
                if(numNullNeighbour % iResetPreviousIter == 0 && numNullNeighbour != 0){
                    previous = null;
                    prevNull++;
                    //System.out.println("Previous set to null");
                }
            }
        }

        /*if(neighbour != null){
            double ratio = (double) numImproving / longestIdleIters;
            System.out.println("Number of GD idle iters = " + longestIdleIters);
            System.out.println("Number of GD improving = " + numImproving);
            System.out.println("Number of GD null = " + numNullNeighbour);
            System.out.println("GD ratio = " + ratio);
            //numImproving = 0;
        }

        if(neighbour == null){
            System.out.println("GD: null = " + prevNull + " Accepted = " + iAcceptedMoves);
            System.out.println("GD null ratio null:accepted = " + iAcceptedMoves/prevNull);
            reset = 0;
            prevNull = 0;
            iAcceptedMoves = 0;
        }*/
        // Logging
        if(solution.getIteration() % 500000 == 0){
            System.out.println("Solution = " + solution.getBestValue());
        }
        return neighbour;
    }

    public void bestSaved(Solution<V,T> solution) {
        iNrIdle = 0;
        iLastImprovingIter = iIter;
    }
    public void solutionUpdated(Solution<V,T> solution) {}
    public void getInfo(Solution<V,T> solution, Map<String, String> info) {}
    public void getInfo(Solution<V,T> solution, Map<String, String> info, Collection<V> variables) {}
    public void bestCleared(Solution<V,T> solution) {}
    public void bestRestored(Solution<V,T> solution){}
    /** Update bound when {@link ItcParameterWeightOscillation} is changed.*/
    public void bestValueChanged(double delta) {
        iBound += delta;
    }
}