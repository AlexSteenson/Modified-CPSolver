package net.sf.cpsolver.itc.heuristics.search;

import net.sf.cpsolver.ifs.heuristics.NeighbourSelection;
import net.sf.cpsolver.ifs.heuristics.StandardNeighbourSelection;
import net.sf.cpsolver.ifs.heuristics.ValueSelection;
import net.sf.cpsolver.ifs.heuristics.VariableSelection;
import net.sf.cpsolver.ifs.model.Neighbour;
import net.sf.cpsolver.ifs.model.Value;
import net.sf.cpsolver.ifs.model.Variable;
import net.sf.cpsolver.ifs.solution.Solution;
import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.itc.heuristics.ItcUnassignedVariableSelection;
import net.sf.cpsolver.itc.heuristics.neighbour.ItcLazyNeighbour;

import java.util.ArrayList;

/**
 * This class uses all acceptance criterion implemented and uses them to make a group decision on if a new solution is accepted
 * After testing it was under performing compared to using the acceptance criterion one at a time, becuase of this
 * we decided not to use the group accept
 * @param <V>
 * @param <T>
 */
public class GroupAccept<V extends Variable<V, T>, T extends Value<V, T>> extends StandardNeighbourSelection<V, T> {

    private boolean consFin = false;
    private NeighbourSelection<V,T> iConstruct;
    private ItcGreatDelugeSeq gd;
    private ItcSimulatedAnnealing sa = null;

    private ArrayList<ItcHillClimber.NeighbourSelector<V, T>> previous;
    private ItcHillClimber.NeighbourSelector<V, T> selector;

    HeuristicSequence neighbourSelector;

    /**
     * Constructor
     * @param properties
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public GroupAccept(DataProperties properties) throws Exception{
        super(properties);

        neighbourSelector = new HeuristicSequence(properties, 1);

        double valueWeight = properties.getPropertyDouble("Itc.Construction.ValueWeight", 0);
        iConstruct = (NeighbourSelection<V,T>)
                Class.forName(properties.getProperty("Itc.Construction",StandardNeighbourSelection.class.getName())).
                        getConstructor(new Class[] {DataProperties.class}).
                        newInstance(new Object[] {properties});
        if (iConstruct instanceof StandardNeighbourSelection) {
            StandardNeighbourSelection<V,T> std = (StandardNeighbourSelection<V,T>)iConstruct;
            std.setValueSelection(
                    (ValueSelection<V,T>)
                            Class.forName(properties.getProperty("Itc.ConstructionValue",ItcTabuSearch.class.getName())).
                                    getConstructor(new Class[] {DataProperties.class}).
                                    newInstance(new Object[] {properties}));
            std.setVariableSelection(
                    (VariableSelection<V,T>)
                            Class.forName(properties.getProperty("Itc.ConstructionVariable", ItcUnassignedVariableSelection.class.getName())).
                                    getConstructor(new Class[] {DataProperties.class}).
                                    newInstance(new Object[] {properties}));
            try {
                std.
                        getValueSelection().
                        getClass().
                        getMethod("setValueWeight", new Class[] {double.class}).
                        invoke(std.getValueSelection(), new Object[] {new Double(valueWeight)});
            } catch (NoSuchMethodException e) {}
        }
        try {
            iConstruct.
                    getClass().
                    getMethod("setValueWeight", new Class[] {double.class}).
                    invoke(iConstruct, new Object[] {new Double(valueWeight)});
        } catch (NoSuchMethodException e) {}

        gd = (ItcGreatDelugeSeq)
                Class.forName(properties.getProperty("Itc.Second", ItcGreatDelugeSeq.class.getName())).
                        getConstructor(new Class[] {DataProperties.class}).
                        newInstance(new Object[] {properties});

        if (properties.getProperty("Itc.Third")!=null)
            sa = (ItcSimulatedAnnealing)Class.forName(properties.getProperty("Itc.Third")).
                    getConstructor(new Class[] {DataProperties.class}).
                    newInstance(new Object[] {properties});
    }

    /**
     * Great Deluge accept
     * @param solution
     * @param neighbour
     * @return
     */
    public int gdAccept(Solution<V,T> solution, Neighbour<V,T> neighbour){
        if(gd.accept(solution, neighbour)){
            return 1;
        }
        return 0;
    }

    /**
     * Simulated annealing accept
     * @param solution
     * @param neighbour
     * @return
     */
    public int saAccept(Solution<V,T> solution, Neighbour<V,T> neighbour){
        if(sa.accept(solution, neighbour)){
            return 1;
        }
        return 0;
    }

    /** Initialization */
    public void init(Solver<V,T> solver) {
        super.init(solver);
        iConstruct.init(solver);
        gd.init(solver);
        neighbourSelector.init(solver);
        if (sa!=null) sa.init(solver);
    }

    /**
     * Select a neighbour and use all the acceptance criterion to determine if its accepted or not
     * GD has a weight of 35% and SA 45%. If its greater than alpha its accepted
     * @param solution
     * @return
     */
    @Override
    public Neighbour<V, T> selectNeighbour(Solution<V, T> solution) {

        // Construct the initial solution
        if(!consFin){
            Neighbour<V,T> n = null;
            n = iConstruct.selectNeighbour(solution);
            if (n!=null) return n;
            consFin = true;
        }else{
            // Get a neighbour
            while(true){
                Neighbour<V,T> n = null;
                while (true){
                    selector = neighbourSelector.getNeighbour(previous, solution.getTime());
                    n = selector.selectNeighbour(solution);
                    if(n != null){
                        break;
                    }
                }

                // Set alpha
                double alpha = 0.5;

                //Get is GD accepts the change
                int gdAccept = gdAccept(solution, n);

                //If SA
                if(sa != null){
                    // Get SA acceptance and determine the group acceptance
                    int saAccept = saAccept(solution, n);
                    if(gdAccept * 0.35 + saAccept * 0.45 > alpha){
                        // Cooling
                        sa.incIter(solution);
                        gd.incIter(solution);
                        return n;
                    }
                }else{ //If SA is not used and just GD
                    if(gdAccept == 1){
                        if(n.value() <= 0){
                            if(previous == null){
                                previous = new ArrayList<>();
                            }
                            previous.add(selector);

                            if(previous.size() == 1){
                                neighbourSelector.updateScore(null, selector, neighbourSelector.isEnded());
                            }else{
                                neighbourSelector.updateScore(previous.get(previous.size()-2), selector, neighbourSelector.isEnded());
                            }

                            if(neighbourSelector.isEnded()){
                                previous = null;
                            }
                        }

                        gd.incIter(solution);
                        return n;
                    }
                }
            }
        }
        return null;
    }
}
