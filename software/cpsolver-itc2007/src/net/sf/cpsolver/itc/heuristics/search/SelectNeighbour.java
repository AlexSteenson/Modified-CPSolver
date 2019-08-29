package net.sf.cpsolver.itc.heuristics.search;

import net.sf.cpsolver.ifs.heuristics.NeighbourSelection;
import net.sf.cpsolver.ifs.model.Neighbour;
import net.sf.cpsolver.ifs.model.Value;
import net.sf.cpsolver.ifs.model.Variable;
import net.sf.cpsolver.ifs.solution.Solution;
import net.sf.cpsolver.ifs.solver.Solver;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.ifs.util.ToolBox;
import net.sf.cpsolver.itc.heuristics.neighbour.selection.ItcNotConflictingMove;
import net.sf.cpsolver.itc.heuristics.neighbour.selection.ItcSwapMove;
import net.sf.cpsolver.itc.heuristics.search.ItcHillClimber.NeighbourSelector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

public class SelectNeighbour<V extends Variable<V, T>, T extends Value<V, T>> implements NeighbourSelection<V,T> {
    private boolean iRandomSelection = false;
    private List<NeighbourSelector<V,T>> iNeighbourSelectors = new ArrayList<NeighbourSelector<V,T>>();
    private boolean iUpdatePoints = false;
    private double iTotalBonus;


    public SelectNeighbour(DataProperties properties) throws Exception{
        iRandomSelection = properties.getPropertyBoolean("HillClimber.Random", iRandomSelection);
        iUpdatePoints = properties.getPropertyBoolean("HillClimber.Update", iUpdatePoints);
        String neighbours = properties.getProperty("HillClimber.Neighbours",
                ItcSwapMove.class.getName()+"@1;"+
                        ItcNotConflictingMove.class.getName()+"@1");
        for (StringTokenizer s = new StringTokenizer(neighbours,";"); s.hasMoreTokens();) {
            String nsClassName = s.nextToken();
            double bonus = 1.0;
            if (nsClassName.indexOf('@')>=0) {
                bonus = Double.parseDouble(nsClassName.substring(nsClassName.indexOf('@')+1));
                nsClassName = nsClassName.substring(0, nsClassName.indexOf('@'));
            }
            Class<?> nsClass = Class.forName(nsClassName);
            @SuppressWarnings("unchecked")
            NeighbourSelection<V,T> ns = (NeighbourSelection<V,T>)nsClass.getConstructor(new Class[] {DataProperties.class}).newInstance(new Object[]{properties});
            addNeighbourSelection(ns,bonus);
        }
    }


    @Override
    public void init(Solver<V, T> solver) {
        iTotalBonus = 0;
        for (NeighbourSelector<V,T> s: iNeighbourSelectors) {
            s.init(solver);
            iTotalBonus += s.getBonus();
        }
    }

    @Override
    public Neighbour<V, T> selectNeighbour(Solution<V, T> solution) {
        return null;
    }

    public NeighbourSelector<V, T> getNeighbour(Solution<V,T> solution){
        while (true) {
            NeighbourSelector<V,T> ns = null;
            if (iRandomSelection) {
                ns = ToolBox.random(iNeighbourSelectors);
            } else {
                double points = (ToolBox.random()*totalPoints());
                for (Iterator<NeighbourSelector<V,T>> i = iNeighbourSelectors.iterator(); i.hasNext(); ) {
                    ns = i.next();
                    points -= (iUpdatePoints?ns.getPoints():ns.getBonus());
                    if (points<=0) break;
                }
            }
            return ns;
        }
    }

    private double totalPoints() {
        if (!iUpdatePoints) return iTotalBonus;
        double total = 0;
        for (NeighbourSelector<V,T> ns: iNeighbourSelectors)
            total += ns.getPoints();
        return total;
    }

    private void addNeighbourSelection(NeighbourSelection<V,T> ns, double bonus) {
        iNeighbourSelectors.add(new NeighbourSelector<V,T>(ns, bonus, iUpdatePoints));
    }
}
