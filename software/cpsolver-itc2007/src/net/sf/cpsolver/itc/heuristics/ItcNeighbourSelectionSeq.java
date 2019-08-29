package net.sf.cpsolver.itc.heuristics;

import net.sf.cpsolver.ifs.solution.Solution;
import net.sf.cpsolver.itc.heuristics.search.*;
import org.apache.log4j.Logger;

import net.sf.cpsolver.ifs.heuristics.NeighbourSelection;
import net.sf.cpsolver.ifs.heuristics.StandardNeighbourSelection;
import net.sf.cpsolver.ifs.heuristics.ValueSelection;
import net.sf.cpsolver.ifs.heuristics.VariableSelection;
import net.sf.cpsolver.ifs.model.Neighbour;
import net.sf.cpsolver.ifs.model.Value;
import net.sf.cpsolver.ifs.model.Variable;
import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.itc.ItcModel;

/**
 * Core search strategy for all three tracks of the ITC 2007.
 * <br><br>
 * At first, a complete solution is to be found using
 * {@link StandardNeighbourSelection} with {@link ItcUnassignedVariableSelection} as 
 * variable selection criterion and {@link ItcTabuSearch} as value selection criterion.
 * A weight of a value can be set for this phase using Itc.Construction.ValueWeight 
 * parameter. Neighbour selection can be redefined using Itc.Construction parameter
 * (contains fully qualified class name of a {@link NeighbourSelection}), or just variable
 * selection or value selection criterion can be redefined using parameters
 * Itc.ConstructionValue and Itc.ConstructionVariable.
 * <br><br>
 * Once a complete solution is found (construction neighbour selection returns null), one, two (or
 * three) neighbour selections are rotated. Each selection is used till it returns null 
 * value in {@link NeighbourSelection#selectNeighbour(Solution)}. Once it happens next selection
 * starts to be used for selection (up till it returns null as well). When the last neighbour selection
 * is used and exhausted, first selection starts to be used again.
 * <br><br>
 * Neighbour selection ordering is set via the config. It can be any order of {@link ItcHillClimberSeq}{@link ItcGreatDelugeSeq}
 * {@link ItcSimulatedAnnealingSeq}
 *
 */
public class ItcNeighbourSelectionSeq<V extends Variable<V, T>, T extends Value<V, T>> extends StandardNeighbourSelection<V, T> {
    private static Logger sLog = Logger.getLogger(ItcNeighbourSelectionSeq.class);
    private int iPhase = 0;
    private NeighbourSelection<V,T> iConstruct, iFirst, iSecond, iThird, iRandom;
    private int numIterGD = 0;
    private int numIterSA = 0;

    private final int HC = 1;
    private final int GD = 2;
    private final int SA = 3;
    private int HCOrder = -1;
    private int GDOrder = -1;
    private int SAOrder = -1;

    private double compSolVal = Double.POSITIVE_INFINITY;
    private int rrCount = 0;
    private int limit = 200;
    
    /** Constructor */
    @SuppressWarnings("unchecked")
	public ItcNeighbourSelectionSeq(DataProperties properties) throws Exception {
        super(properties);
        HCOrder = properties.getPropertyInt("Sequence.HC", HCOrder);
        GDOrder = properties.getPropertyInt("Sequence.GD", GDOrder);
        SAOrder = properties.getPropertyInt("Sequence.SA", SAOrder);
        double valueWeight = properties.getPropertyDouble("Itc.Construction.ValueWeight", 0);
        iConstruct = 
            (NeighbourSelection<V,T>)
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
                    Class.forName(properties.getProperty("Itc.ConstructionVariable",ItcUnassignedVariableSelection.class.getName())).
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

        iRandom = (NeighbourSelection<V,T>)
            Class.forName(properties.getProperty("Itc.First", RandomRestart.class.getName())).
            getConstructor(new Class[] {DataProperties.class}).
            newInstance(new Object[] {properties});

        iFirst = (NeighbourSelection<V,T>)
                Class.forName(properties.getProperty("Itc.First", ItcHillClimberSeq.class.getName())).
                        getConstructor(new Class[] {DataProperties.class}).
                        newInstance(new Object[] {properties});
        
        iSecond = (NeighbourSelection<V,T>)
            Class.forName(properties.getProperty("Itc.Second", ItcGreatDelugeSeq.class.getName())).
            getConstructor(new Class[] {DataProperties.class}).
            newInstance(new Object[] {properties});
        
        if (properties.getProperty("Itc.Third")!=null)
            iThird = (NeighbourSelection<V,T>)Class.forName(properties.getProperty("Itc.Third")).
                getConstructor(new Class[] {DataProperties.class}).
                newInstance(new Object[] {properties});
    }
    
    /** Initialization */
    public void init(Solver<V,T> solver) {
        super.init(solver);
        iConstruct.init(solver);
        iFirst.init(solver);
        iSecond.init(solver);
        if (iThird!=null) iThird.init(solver);
    }
    
    /** Change phase, i.e., what selector is to be used next */
    protected void incPhase(Solution<V,T> solution, String name) {

        // Finds the next acceptance in the ordering
        switch (iPhase){
            case 0 :
                if(HCOrder == 0) iPhase = HC;
                else if(GDOrder == 0) iPhase = GD;
                else if(SAOrder == 0) iPhase = SA;
                break;
            case HC :
                if(GDOrder == HCOrder + 1) iPhase = GD;
                else if(SAOrder == HCOrder + 1) iPhase = SA;
                else if(SAOrder == 0) iPhase = SA;
                else if(GDOrder == 0) iPhase = GD;
                break;
            case GD :
                if(HCOrder == GDOrder + 1) iPhase = HC;
                else if(SAOrder == GDOrder + 1) iPhase = SA;
                else if(SAOrder == 0) iPhase = SA;
                else if(HCOrder == 0) iPhase = HC;
                break;
            case SA :
                if(GDOrder == SAOrder + 1) iPhase = GD;
                else if(HCOrder == SAOrder + 1) iPhase = HC;
                else if(HCOrder == 0) iPhase = HC;
                else if(GDOrder == 0) iPhase = GD;
                break;
        }

        // Logging
        if (sLog.isInfoEnabled()) {
            ItcModel<V,T> m = (ItcModel<V,T>)solution.getModel();
            sLog.info("**CURR["+solution.getIteration()+"]** P:"+Math.round(m.getTotalValue())+
                    " ("+m.csvLine()+")");
            sLog.info("Phase "+name);
        }
    }
    
    /** Neighbour selection  -- based on the phase, construction strategy is used first,
     * than it iterates between the given neighbour selections*/
    public Neighbour<V,T> selectNeighbour(Solution<V,T> solution) {
        Neighbour<V,T> n = null;
        switch (iPhase) {
            case 0 :
                n = iConstruct.selectNeighbour(solution);
                checkRestart(solution);
                if (n!=null) return n;
                incPhase(solution, "first");
                System.out.println("Solution after construction = " + solution.getBestValue());
                return selectNeighbour(solution);
            case HC :
                n = iFirst.selectNeighbour(solution);
                //numIterSA++;
                if (n!=null) return n;
                incPhase(solution, "HC");
                System.out.println("Solution after HC = " + solution.getBestValue() + " " + solution.isBestComplete());
                return selectNeighbour(solution);
            case GD :
                n = iSecond.selectNeighbour(solution);
                //numIterSA++;
                if (n!=null) return n;
                incPhase(solution, "GD");
                System.out.println("Solution after GD = " + solution.getBestValue() + " " + solution.isBestComplete());
                return selectNeighbour(solution);
            case SA :
                n = (iThird==null?null:iThird.selectNeighbour(solution));
                //numIterSA++;
                if (n!=null) return n;
                incPhase(solution, "SA");
                System.out.println("Solution after SA = " + solution.getBestValue() + " " + solution.isBestComplete());
                return selectNeighbour(solution);
            case 4 : // Resets the solution
                solution.getModel().clearBest();
                solution.getModel().restoreBest();
                iPhase = 0;
                rrCount = 0;
                System.out.println("Solution after restart = " + solution.getBestValue());
                return selectNeighbour(solution);
            default :
                iPhase = 1;
                return selectNeighbour(solution);
        }
    }

    /**
     * If the construction can't find a solution in x iterations, restart the construction process
     * @param solution
     */
    public void checkRestart(Solution solution){
        if(solution.getModel().nrUnassignedVariables() == compSolVal){
            rrCount++;
            if(rrCount == limit){
                iPhase = 4;
                System.out.println("Restart!");
            }
        }else{
            compSolVal = solution.getModel().nrUnassignedVariables();
            rrCount = 0;
        }
    }

}
