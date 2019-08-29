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

import java.util.*;

/**
 * This is the main class for sequence creation. Two matrices are kept to store the transition score and the
 * acceptance scores. The next low-level heuristic is selected using the transScore matrix and with an array
 * logging the current sequence. The last llh in the sequence is used tp index into transScore.
 * The heuristic selection method specified in the config file is used to select the next llh.
 * The asScore martix is used to determine if the sequence is ended at the selected llh.
 * If the sequence produces a new best solution value the transScore and asScore matrices are updated using the
 * learning method specified in the config.
 * All the scores are shared between the acceptance methods.
 * @param <V>
 * @param <T>
 */
public class HeuristicSequence<V extends Variable<V, T>, T extends Value<V, T>> implements NeighbourSelection<V, T> {

    private final int HC = 0;
    private final int GD = 1;
    private final int SA = 2;

    private ArrayList<NeighbourSelector<V, T>> iNeighbourSelectors = new ArrayList<NeighbourSelector<V, T>>();
    private static double transScore[][];
    private static double asScore[][];
    private boolean endSequence;
    private boolean takeBest = false;

    private boolean sequence = true;
    private int selectionType;

    private boolean iUpdatePoints = false;

    /**
     * Constructor
     * @param properties
     * @param type
     * @throws Exception
     */
    public HeuristicSequence(DataProperties properties, int type) throws Exception {
        String neighbours;

        sequence = properties.getPropertyBoolean("Sequence.Sequence", true);
        selectionType = properties.getPropertyInt("Sequence.SelectionType", 1);

        // Get the neighbours depending on the acceptance strategy as they can be different
        switch (type) {
            case (HC):
                neighbours = properties.getProperty("HillClimber.Neighbours",
                        ItcSwapMove.class.getName() + "@1;" +
                                ItcNotConflictingMove.class.getName() + "@1");
                break;
            case (GD):
                neighbours = properties.getProperty("GreatDeluge.Neighbours",
                        ItcSwapMove.class.getName()+"@1;"+
                                ItcNotConflictingMove.class.getName()+"@1");
                break;
            case (SA):
                neighbours = properties.getProperty("SimulatedAnnealing.Neighbours",
                        ItcSwapMove.class.getName() + "@1;" +
                                ItcNotConflictingMove.class.getName() + "@1");
                break;
            default:
                neighbours = properties.getProperty("HillClimber.Neighbours",
                        ItcSwapMove.class.getName() + "@1;" +
                                ItcNotConflictingMove.class.getName() + "@1");
        }

        // Read all neighbours from config file
        for (StringTokenizer s = new StringTokenizer(neighbours, ";"); s.hasMoreTokens(); ) {
            String nsClassName = s.nextToken();
            double bonus = 1.0;
            Class<?> nsClass = Class.forName(nsClassName);
            @SuppressWarnings("unchecked")
            NeighbourSelection<V, T> ns = (NeighbourSelection<V, T>) nsClass.getConstructor(new Class[]{DataProperties.class}).newInstance(new Object[]{properties});
            // Add neighbour
            addNeighbourSelection(ns, bonus);
        }
    }

    /**
     * Add neighbours to array list
     * @param ns
     * @param bonus
     */
    private void addNeighbourSelection(NeighbourSelection<V, T> ns, double bonus) {
        iNeighbourSelectors.add(new NeighbourSelector<V, T>(ns, bonus, iUpdatePoints));
    }

    /**
     * init
     * @param solver
     */
    @Override
    public void init(Solver<V, T> solver) {
        int nNeighbours = iNeighbourSelectors.size();
        transScore = new double[nNeighbours + 1][nNeighbours];
        // Fill each row with 1.0
        for (double[] row : transScore)
            Arrays.fill(row, 1.0);
        asScore = new double[nNeighbours][2];
        // Fill each row with 1.0
        for (double[] row : asScore)
            Arrays.fill(row, 1.0);
    }

    @Override
    public Neighbour<V, T> selectNeighbour(Solution<V, T> solution) {
        return null;
    }


    public NeighbourSelector<V, T> getNeighbour(ArrayList<NeighbourSelector<V, T>> previous, double time){

        // Get last llh in sequence
        int last = 0;
        if(previous != null){
            last = getIndex(previous.get(previous.size() - 1));
        }

        return getSelector(last, previous);
    }

    /**
     * Get the possible selectors based on the heuristic selection type.
     * Get the next llh and if the sequence is ended
     * retrun the llh
     * @param last
     * @param previous
     * @return
     */
    public NeighbourSelector<V,T> getSelector(int last, ArrayList<NeighbourSelector<V, T>> previous){
        NeighbourSelector<V,T> ns = null;

        ArrayList<NeighbourSelector<V, T>> possibleSelectors = new ArrayList<>();
        ArrayList<Integer> selectorIndex = new ArrayList<>();

        // Get possible llh that can be selected
        if(selectionType == 1){
            possibleSelectors = iNeighbourSelectors;
        }else{
            if(previous == null){
                possibleSelectors = iNeighbourSelectors;
            }else{
                // Get possible selectors - Always needed.
                for (Iterator<NeighbourSelector<V,T>> i = iNeighbourSelectors.iterator(); i.hasNext(); ) {
                    ns = i.next();
                    if(previous.contains(ns)){
                        continue;
                    }else{
                        possibleSelectors.add(ns);
                        selectorIndex.add(getIndex(ns) - 1);
                    }
                }
            }
        }
        NeighbourSelector<V,T> selector = null;
        // Get llh based on heuristic selection type
        if(selectionType == 0){
            selector = getSelRW(last, possibleSelectors, selectorIndex);
        }else{
            selector = getSelTorn(last, possibleSelectors, 2);
        }

        // determine if the sequence is ended based on its size
        if(previous != null){
            if(previous.size() == iNeighbourSelectors.size() - 1){
                endSequence = true;
                return selector;
            }
        }

        // Roulette wheel to determine if the sequence is ended
        int index = getIndex(selector) - 1;
        double totalPoints = asScore[index][0] + asScore[index][1];
        double points = (ToolBox.random()*totalPoints);

        points -= asScore[index][0];

        endSequence = (!sequence || !(points <= 0));

        return selector;
    }

    /**
     * Gets the llh with the highest score
     * @param last
     * @param possibleSelectors
     * @return
     */
    public NeighbourSelector<V, T> getBest(int last, ArrayList<NeighbourSelector<V, T>> possibleSelectors){
        NeighbourSelector<V,T> ns = null;
        NeighbourSelector<V,T> bestSel = null;
        double best = Double.NEGATIVE_INFINITY;
        for(Iterator<NeighbourSelector<V,T>> i = possibleSelectors.iterator(); i.hasNext();){
            ns = i.next();
            double score = transScore[last][getIndex(ns) - 1];
            if( score >= best){
                best = score;
                bestSel = ns;
            }
        }

        return bestSel;
    }

    /**
     * get next llh with roulette wheel
     * @param last
     * @param possibleSelectors
     * @param selectorIndex
     * @return
     */
    public NeighbourSelector<V, T> getSelRW(int last, ArrayList<NeighbourSelector<V, T>> possibleSelectors, ArrayList<Integer> selectorIndex){

        double totalPoints = 0;

        // Get total points
        for (int j = 0; j < selectorIndex.size(); j++)
            totalPoints += transScore[last][j];

        // Get a random number between 0 and max points
        double points = (ToolBox.random()*totalPoints);
        int index = 0;

        // Remove the score of llh until a llh makes the points get below 0
        NeighbourSelector<V,T> ns = null;
        for (Iterator<NeighbourSelector<V,T>> i = possibleSelectors.iterator(); i.hasNext(); ) {
            ns = i.next();

            if(selectorIndex.size() == 0)
                points -= transScore[last][index];
            else
                points -= transScore[last][selectorIndex.get(index)];
            if (points<=0) break;
            index++;
        }

        return ns;
    }

    /**
     * Tournament selection for selecting llh. It only does 1 pass as all we need is 1 llh
     * @param last
     * @param possibleSelectors
     * @param cap
     * @return
     */
    public NeighbourSelector<V, T> getSelTorn(int last, ArrayList<NeighbourSelector<V, T>> possibleSelectors, int cap){

        NeighbourSelector<V,T> ns = null;
        // Remove added selector from poss to reduce iterations
        ArrayList<NeighbourSelector<V,T>> torn = new ArrayList<>();
        NeighbourSelector<V,T> bestSel = null;
        // Torn size - 2
        int limit = possibleSelectors.size()<cap ? possibleSelectors.size() : cap;

        // Fill torn
        while(torn.size() != limit){
            int randIndex = (int) Math.floor(ToolBox.random()*possibleSelectors.size());
            NeighbourSelector<V,T> rand = possibleSelectors.get(randIndex);
            if(!torn.contains(rand)){
                torn.add(rand);
            }
        }

        // Get best llh from torn
        double best = Double.NEGATIVE_INFINITY;
        for(Iterator<NeighbourSelector<V,T>> i = torn.iterator(); i.hasNext();){
            ns = i.next();
            double score = transScore[last][getIndex(ns) - 1];
            if( score >= best){
                best = score;
                bestSel = ns;
            }
        }
        return bestSel;
    }

    /**
     * Returns the index of a specific llh in the array list
     * @param selector
     * @return
     */
    public int getIndex(NeighbourSelector<V, T> selector){
        if(selector == null)
            return 0;

        return iNeighbourSelectors.indexOf(selector) + 1;
    }

    /**
     * Updates the scores linearly
     * @param previous
     * @param current
     * @param ended
     */
    public void updateScore(NeighbourSelector<V, T> previous, NeighbourSelector<V, T> current, boolean ended){
        int currentIndex = getIndex(current) - 1;

        if(previous == null){
            transScore[0][currentIndex]++;
        }else{
            int previousIndex = getIndex(previous);
            transScore[previousIndex][currentIndex]++;
        }
        if(ended) {
            asScore[currentIndex][1]++;
        }else {
            asScore[currentIndex][0]++;
        }
    }

    /**
     * Updates the scores using the change in solution value
     * @param previous
     * @param current
     * @param ended
     * @param delta
     */
    public void updateScoreDelta(NeighbourSelector<V, T> previous, NeighbourSelector<V, T> current, boolean ended, double delta){
        int currentIndex = getIndex(current) - 1;

        if(previous == null){
            transScore[0][currentIndex]+= delta;
        }else{
            int previousIndex = getIndex(previous);
            transScore[previousIndex][currentIndex]+= delta;
        }
        if(ended) {
            asScore[currentIndex][1]+= delta;
        }else {
            asScore[currentIndex][0]+= delta;
        }
    }

    /**
     * Updates the scores depending on the time in the search process. The later in the search the higher the score
     * @param previous
     * @param current
     * @param ended
     * @param time
     */
    public void updateScoreNL(NeighbourSelector<V, T> previous, NeighbourSelector<V, T> current, boolean ended, double time){
        int currentIndex = getIndex(current) - 1;
        int nl = (int) Math.floor(Math.exp(time/30));

        if(previous == null){
            transScore[0][currentIndex]+= nl;
        }else{
            int previousIndex = getIndex(previous);
            transScore[previousIndex][currentIndex]+= nl;
        }
        if(ended) {
            asScore[currentIndex][1]+= nl;
        }else {
            asScore[currentIndex][0]+= nl;
        }
    }

    /**
     * Decrements the score linearly - not used
     * @param previous
     * @param current
     * @param ended
     */
    public void decScore(NeighbourSelector<V, T> previous, NeighbourSelector<V, T> current, boolean ended){

        int currentIndex = getIndex(current) - 1;
        int previousIndex = getIndex(previous);

        if(previous == null){
            if(transScore[0][currentIndex] > 1)
                transScore[0][currentIndex]--;
        }else{
            if(transScore[previousIndex][currentIndex] > 1)
                transScore[previousIndex][currentIndex]--;
        }
        if(ended)
            if(asScore[currentIndex][1] > 1)
                asScore[currentIndex][1]--;
        else
            if(asScore[currentIndex][0] > 1)
                asScore[currentIndex][0]--;
    }

    // Returns is the sequence is ended
    public boolean isEnded(){
        return endSequence;
    }

    // Resets the scores
    public void resetScores(){
        // Fill each row with 1.0
        for (double[] row : transScore)
            Arrays.fill(row, 1.0);
        // Fill each row with 1.0
        for (double[] row : asScore)
            Arrays.fill(row, 1.0);
    }

}