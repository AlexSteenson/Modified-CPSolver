//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package net.sf.cpsolver.ifs.solver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import net.sf.cpsolver.ifs.extension.Extension;
import net.sf.cpsolver.ifs.heuristics.NeighbourSelection;
import net.sf.cpsolver.ifs.model.Model;
import net.sf.cpsolver.ifs.model.Neighbour;
import net.sf.cpsolver.ifs.model.Value;
import net.sf.cpsolver.ifs.model.Variable;
import net.sf.cpsolver.ifs.perturbations.PerturbationsCounter;
import net.sf.cpsolver.ifs.solution.Solution;
import net.sf.cpsolver.ifs.solution.SolutionComparator;
import net.sf.cpsolver.ifs.termination.TerminationCondition;
import net.sf.cpsolver.ifs.util.DataProperties;
import net.sf.cpsolver.ifs.util.JProf;
import net.sf.cpsolver.ifs.util.Progress;
import net.sf.cpsolver.ifs.util.ToolBox;
import org.apache.log4j.Logger;

public class Solver<V extends Variable<V, T>, T extends Value<V, T>> {
    public static int THREAD_PRIORITY = 3;
    protected static Logger sLogger = Logger.getLogger(Solver.class);
    protected Solution<V, T> iCurrentSolution = null;
    protected Solution<V, T> iLastSolution = null;
    protected boolean iStop = false;
    protected Solver<V, T>.SolverThread iSolverThread = null;
    private DataProperties iProperties = null;
    private TerminationCondition<V, T> iTerminationCondition = null;
    private SolutionComparator<V, T> iSolutionComparator = null;
    private PerturbationsCounter<V, T> iPerturbationsCounter = null;
    private NeighbourSelection<V, T> iNeighbourSelection = null;
    private List<Extension<V, T>> iExtensions = new ArrayList();
    private List<SolverListener<V, T>> iSolverListeners = new ArrayList();
    private int iSaveBestUnassigned = 0;
    private boolean iUpdateProgress = true;
    private Progress iProgress;
    private boolean iValueExtraUsed = false;
    private boolean iVariableExtraUsed = false;

    public Solver(DataProperties properties) {
        this.iProperties = properties;
    }

    public void dispose() {
        this.iExtensions.clear();
        this.iSolverListeners.clear();
        this.iTerminationCondition = null;
        this.iSolutionComparator = null;
        this.iPerturbationsCounter = null;
        this.iNeighbourSelection = null;
    }

    public void setTerminalCondition(TerminationCondition<V, T> terminationCondition) {
        this.iTerminationCondition = terminationCondition;
    }

    public void setSolutionComparator(SolutionComparator<V, T> solutionComparator) {
        this.iSolutionComparator = solutionComparator;
    }

    public void setNeighbourSelection(NeighbourSelection<V, T> neighbourSelection) {
        this.iNeighbourSelection = neighbourSelection;
    }

    public void setPerturbationsCounter(PerturbationsCounter<V, T> perturbationsCounter) {
        this.iPerturbationsCounter = perturbationsCounter;
    }

    public void addExtension(Extension<V, T> extension) {
        if (extension.useValueExtra() && this.iValueExtraUsed) {
            sLogger.warn("Unable to add an extension " + extension + " -- value extra is already used.");
        } else if (extension.useVariableExtra() && this.iVariableExtraUsed) {
            sLogger.warn("Unable to add extension " + extension + " -- variable extra is already used.");
        } else {
            this.iValueExtraUsed |= extension.useValueExtra();
            this.iValueExtraUsed = this.iVariableExtraUsed | extension.useVariableExtra();
            this.iExtensions.add(extension);
        }
    }

    public TerminationCondition<V, T> getTerminationCondition() {
        return this.iTerminationCondition;
    }

    public SolutionComparator<V, T> getSolutionComparator() {
        return this.iSolutionComparator;
    }

    public NeighbourSelection<V, T> getNeighbourSelection() {
        return this.iNeighbourSelection;
    }

    public PerturbationsCounter<V, T> getPerturbationsCounter() {
        return this.iPerturbationsCounter;
    }

    public List<Extension<V, T>> getExtensions() {
        return this.iExtensions;
    }

    public void addSolverListener(SolverListener<V, T> listener) {
        this.iSolverListeners.add(listener);
    }

    public void removeSolverListener(SolverListener<V, T> listener) {
        this.iSolverListeners.remove(listener);
    }

    public List<SolverListener<V, T>> getSolverListeners() {
        return this.iSolverListeners;
    }

    public DataProperties getProperties() {
        return this.iProperties;
    }

    protected void autoConfigure() {
        try {
            boolean mpp = this.getProperties().getPropertyBoolean("General.MPP", false);
            String terminationConditionClassName = this.getProperties().getProperty("Termination.Class", mpp ? "net.sf.cpsolver.ifs.termination.MPPTerminationCondition" : "net.sf.cpsolver.ifs.termination.GeneralTerminationCondition");
            sLogger.info("Using " + terminationConditionClassName);
            Class<?> terminationConditionClass = Class.forName(terminationConditionClassName);
            Constructor<?> terminationConditionConstructor = terminationConditionClass.getConstructor(DataProperties.class);
            this.setTerminalCondition((TerminationCondition)terminationConditionConstructor.newInstance(this.getProperties()));
            String solutionComparatorClassName = this.getProperties().getProperty("Comparator.Class", mpp ? "net.sf.cpsolver.ifs.solution.MPPSolutionComparator" : "net.sf.cpsolver.ifs.solution.GeneralSolutionComparator");
            sLogger.info("Using " + solutionComparatorClassName);
            Class<?> solutionComparatorClass = Class.forName(solutionComparatorClassName);
            Constructor<?> solutionComparatorConstructor = solutionComparatorClass.getConstructor(DataProperties.class);
            this.setSolutionComparator((SolutionComparator)solutionComparatorConstructor.newInstance(this.getProperties()));
            String neighbourSelectionClassName = this.getProperties().getProperty("Neighbour.Class", "net.sf.cpsolver.ifs.heuristics.StandardNeighbourSelection");
            sLogger.info("Using " + neighbourSelectionClassName);
            Class<?> neighbourSelectionClass = Class.forName(neighbourSelectionClassName);
            Constructor<?> neighbourSelectionConstructor = neighbourSelectionClass.getConstructor(DataProperties.class);
            this.setNeighbourSelection((NeighbourSelection)neighbourSelectionConstructor.newInstance(this.getProperties()));
            String perturbationCounterClassName = this.getProperties().getProperty("PerturbationCounter.Class", "net.sf.cpsolver.ifs.perturbations.DefaultPerturbationsCounter");
            sLogger.info("Using " + perturbationCounterClassName);
            Class<?> perturbationCounterClass = Class.forName(perturbationCounterClassName);
            Constructor<?> perturbationCounterConstructor = perturbationCounterClass.getConstructor(DataProperties.class);
            this.setPerturbationsCounter((PerturbationsCounter)perturbationCounterConstructor.newInstance(this.getProperties()));
            Iterator i$ = this.iExtensions.iterator();

            while(i$.hasNext()) {
                Extension<V, T> extension = (Extension)i$.next();
                extension.unregister(this.iCurrentSolution.getModel());
            }

            this.iExtensions.clear();
            String extensionClassNames = this.getProperties().getProperty("Extensions.Classes", (String)null);
            if (extensionClassNames != null) {
                StringTokenizer extensionClassNameTokenizer = new StringTokenizer(extensionClassNames, ";");

                while(extensionClassNameTokenizer.hasMoreTokens()) {
                    String extensionClassName = extensionClassNameTokenizer.nextToken();
                    sLogger.info("Using " + extensionClassName);
                    Class<?> extensionClass = Class.forName(extensionClassName);
                    Constructor<?> extensionConstructor = extensionClass.getConstructor(Solver.class, DataProperties.class);
                    this.addExtension((Extension)extensionConstructor.newInstance(this, this.getProperties()));
                }
            }
        } catch (Exception var19) {
            sLogger.error("Unable to autoconfigure solver.", var19);
        }

    }

    public void clearBest() {
        if (this.iCurrentSolution != null) {
            this.iCurrentSolution.clearBest();
        }

    }

    public void setInitalSolution(Solution<V, T> solution) {
        this.iCurrentSolution = solution;
        this.iLastSolution = null;
    }

    public void setInitalSolution(Model<V, T> model) {
        this.iCurrentSolution = new Solution(model, 0L, 0.0D);
        this.iLastSolution = null;
    }

    public void start() {
        this.iSolverThread = new Solver.SolverThread();
        this.iSolverThread.setPriority(THREAD_PRIORITY);
        this.iSolverThread.start();
    }

    public Thread getSolverThread() {
        return this.iSolverThread;
    }

    public void init() {
    }

    private boolean isUpdateProgress() {
        return this.iUpdateProgress;
    }

    public void setUpdateProgress(boolean updateProgress) {
        this.iUpdateProgress = updateProgress;
    }

    public Solution<V, T> lastSolution() {
        return this.iLastSolution == null ? this.iCurrentSolution : this.iLastSolution;
    }

    public Solution<V, T> currentSolution() {
        return this.iCurrentSolution;
    }

    public void initSolver() {
        long seed = this.getProperties().getPropertyLong("General.Seed", System.currentTimeMillis());
        ToolBox.setSeed(seed);
        this.iSaveBestUnassigned = this.getProperties().getPropertyInt("General.SaveBestUnassigned", 0);
        this.clearBest();
        if (this.iProperties.getPropertyBoolean("Solver.AutoConfigure", true)) {
            this.autoConfigure();
        }

        Iterator f = this.iExtensions.iterator();

        while(f.hasNext()) {
            Extension<V, T> extension = (Extension)f.next();
            extension.register(this.iCurrentSolution.getModel());
        }

        this.iCurrentSolution.init(this);
        this.getNeighbourSelection().init(this);
        if (this.getPerturbationsCounter() != null) {
            this.getPerturbationsCounter().init(this);
        }

        if (this.iProperties.getPropertyBoolean("General.SaveConfiguration", false)) {
            f = null;

            try {
                FileOutputStream file = new FileOutputStream(this.iProperties.getProperty("General.Output") + File.separator + this.iProperties.getProperty("General.ProblemName", "ifs") + ".properties");
                this.iProperties.store(file, this.iProperties.getProperty("General.ProblemNameLong", "Iterative Forward Search") + "  -- configuration file");
                file.flush();
                file.close();
                file = null;
            } catch (Exception var13) {
                sLogger.error("Unable to store configuration file :-(", var13);
            }
        }

    }

    public void stopSolver() {
        this.stopSolver(true);
    }

    public void stopSolver(boolean join) {
        if (this.getSolverThread() != null) {
            this.iStop = true;
            if (join) {
                try {
                    this.getSolverThread().join();
                } catch (InterruptedException var3) {
                    ;
                }
            }
        }

    }

    public boolean isRunning() {
        return this.getSolverThread() != null;
    }

    protected void onStop() {
    }

    protected void onStart() {
    }

    protected void onFinish() {
    }

    protected void onFailure() {
    }

    protected void onAssigned(double startTime) {
    }

    public boolean isStop() {
        return this.iStop;
    }

    protected class SolverThread extends Thread {
        protected SolverThread() {
        }

        public void run() {
            try {
                Solver.this.iStop = false;
                this.setName("Solver");
                Solver.this.iProgress = Progress.getInstance(Solver.this.iCurrentSolution.getModel());
                Solver.this.iProgress.setStatus("Solving problem ...");
                Solver.this.iProgress.setPhase("Initializing solver");
                Solver.this.initSolver();
                Solver.this.onStart();
                double startTime = JProf.currentTimeSec();
                if (Solver.this.isUpdateProgress()) {
                    if (Solver.this.iCurrentSolution.getBestInfo() == null) {
                        Solver.this.iProgress.setPhase("Searching for initial solution ...", (long)Solver.this.iCurrentSolution.getModel().variables().size());
                    } else {
                        Solver.this.iProgress.setPhase("Improving found solution ...");
                    }
                }

                long prog = 9999L;
                Solver.sLogger.info("Initial solution:" + ToolBox.dict2string(Solver.this.iCurrentSolution.getInfo(), 1));
                if ((Solver.this.iSaveBestUnassigned < 0 || Solver.this.iSaveBestUnassigned >= Solver.this.iCurrentSolution.getModel().nrUnassignedVariables()) && (Solver.this.iCurrentSolution.getBestInfo() == null || Solver.this.getSolutionComparator().isBetterThanBestSolution(Solver.this.iCurrentSolution))) {
                    if (Solver.this.iCurrentSolution.getModel().nrUnassignedVariables() == 0) {
                        Solver.sLogger.info("Complete solution " + ToolBox.dict2string(Solver.this.iCurrentSolution.getInfo(), 1) + " was found.");
                    }

                    Solution var5 = Solver.this.iCurrentSolution;
                    synchronized(Solver.this.iCurrentSolution) {
                        Solver.this.iCurrentSolution.saveBest();
                    }
                }

                if (Solver.this.iCurrentSolution.getModel().variables().isEmpty()) {
                    Solver.this.iProgress.error("Nothing to solve.");
                    Solver.this.iStop = true;
                }

                while(true) {
                    while(!Solver.this.iStop && Solver.this.getTerminationCondition().canContinue(Solver.this.iCurrentSolution)) {
                        Neighbour<V, T> neighbour = Solver.this.getNeighbourSelection().selectNeighbour(Solver.this.iCurrentSolution);
                        Iterator i$ = Solver.this.iSolverListeners.iterator();

                        while(i$.hasNext()) {
                            SolverListener<V, T> listener = (SolverListener)i$.next();
                            if (!listener.neighbourSelected(Solver.this.iCurrentSolution.getIteration(), neighbour)) {
                                neighbour = null;
                            }
                        }

                        Solution var17;
                        if (neighbour == null) {
                            Solver.sLogger.debug("No neighbour selected.");
                            var17 = Solver.this.iCurrentSolution;
                            synchronized(Solver.this.iCurrentSolution) {
                                Solver.this.iCurrentSolution.update(JProf.currentTimeSec() - startTime);
                            }
                        } else {
                            var17 = Solver.this.iCurrentSolution;
                            synchronized(Solver.this.iCurrentSolution) {
                                neighbour.assign(Solver.this.iCurrentSolution.getIteration());
                                Solver.this.iCurrentSolution.update(JProf.currentTimeSec() - startTime);
                            }

                            Solver.this.onAssigned(startTime);
                            if ((Solver.this.iSaveBestUnassigned < 0 || Solver.this.iSaveBestUnassigned >= Solver.this.iCurrentSolution.getModel().nrUnassignedVariables()) &&  Solver.this.getSolutionComparator().isBetterThanBestSolution(Solver.this.iCurrentSolution)) {
                                if (Solver.this.iCurrentSolution.getModel().nrUnassignedVariables() == 0) {
                                    Solver.this.iProgress.debug("Complete solution of value " + Solver.this.iCurrentSolution.getModel().getTotalValue() + " was found.");
                                }

                                var17 = Solver.this.iCurrentSolution;
                                synchronized(Solver.this.iCurrentSolution) {
                                    //System.out.println("improving sol before: " + Solver.this.iCurrentSolution.getBestValue());
                                    Solver.this.iCurrentSolution.saveBest();
                                    //System.out.println("improving sol after: " + Solver.this.iCurrentSolution.getBestValue());
                                }
                            }

                            if (Solver.this.isUpdateProgress()) {
                                if (Solver.this.iCurrentSolution.getBestInfo() != null && Solver.this.iCurrentSolution.getModel().getBestUnassignedVariables() == 0) {
                                    ++prog;
                                    if (prog == 10000L) {
                                        Solver.this.iProgress.setPhase("Improving found solution ...");
                                        prog = 0L;
                                    } else {
                                        Solver.this.iProgress.setProgress(prog / 100L);
                                    }
                                } else if ((Solver.this.iCurrentSolution.getBestInfo() == null || Solver.this.iCurrentSolution.getModel().getBestUnassignedVariables() > 0) && (long)(Solver.this.iCurrentSolution.getModel().variables().size() - Solver.this.iCurrentSolution.getModel().nrUnassignedVariables()) > Solver.this.iProgress.getProgress()) {
                                    Solver.this.iProgress.setProgress((long)(Solver.this.iCurrentSolution.getModel().variables().size() - Solver.this.iCurrentSolution.getModel().nrUnassignedVariables()));
                                }
                            }
                        }
                    }

                    Solver.this.iLastSolution = Solver.this.iCurrentSolution;
                    Solver.this.iProgress.setPhase("Done", 1L);
                    Solver.this.iProgress.incProgress();
                    Solver.this.iSolverThread = null;
                    if (Solver.this.iStop) {
                        Solver.sLogger.debug("Solver stopped.");
                        Solver.this.iProgress.setStatus("Solver stopped.");
                        Solver.this.onStop();
                    } else {
                        Solver.sLogger.debug("Solver done.");
                        Solver.this.iProgress.setStatus("Solver done.");
                        Solver.this.onFinish();
                    }
                    break;
                }
            } catch (Exception var15) {
                Solver.sLogger.error(var15.getMessage(), var15);
                Solver.this.iProgress.fatal("Solver failed, reason:" + var15.getMessage(), var15);
                Solver.this.iProgress.setStatus("Solver failed.");
                Solver.this.onFailure();
            }

            Solver.this.iSolverThread = null;
        }
    }
}
