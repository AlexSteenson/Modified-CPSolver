package net.sf.cpsolver.itc;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Read single value from file - very low
 * test over 5 - 10 runs
 * get average
 * log best average and value
 * inc value
 * repeat until cant find better score
 */
public class ParamTune {
    static ItcTest runner = new ItcTest();
    private static double bestAverageScore = Double.POSITIVE_INFINITY;
    private static double bestValue = -1;
    private static double initValue;
    private static final int RUNNUM = 10;
    private static double lowestValue = Double.POSITIVE_INFINITY;
    private static boolean isLowest = false;


    /** Main method -- parse input arguments, create solver, solve, and output solution on exit */
    public static void main(String[] args) {

        for(int j = 5; j <= 6; j++){
            String[] arg = {"tim", "data/tim/comp-2007-2-" + j + ".tim", "tim_out/se_tim_sol1.out"};
            while(true){
                initValue = readValue();

                for(int i = 0; i < RUNNUM; i++){
                    //runner.run(arg);
                }

                double sum = readSolutionValue();

                if(sum <= bestAverageScore){
                    bestAverageScore = sum;
                    bestValue = initValue;
                    incValue(-1, -1);
                    clearSolValues();
                }else if(isLowest) {
                    bestValue = initValue;
                    incValue(-1, -1);
                    clearSolValues();
                }else{
                    break;
                }
                isLowest = false;
            }
            // Reset the normal one
            incValue(-1, 0.82);
            // Write best value to specific txt
            incValue(j, bestValue);
            clearSolValues();
            isLowest = false;
            bestAverageScore = Double.POSITIVE_INFINITY;
            lowestValue = Double.POSITIVE_INFINITY;
            bestValue = -1;
        }
    }

    /**
     * increment the value thats being tuned by 0.01 and rewrite it to the file for use elsewhere in the solver
     * @param instance
     * @param num
     */
    public static void incValue(int instance, double num){
        initValue += 0.01;
        String newNum = num + "";
        String val = initValue + "";
        try {
            if(instance == -1){
                if(num == -1)
                    Files.write(Paths.get("value.txt"), val.getBytes(), StandardOpenOption.WRITE);
                else
                    Files.write(Paths.get("value.txt"), newNum.getBytes(), StandardOpenOption.WRITE);
            }else{
                Files.write(Paths.get("value" + instance + ".txt"), newNum.getBytes(), StandardOpenOption.WRITE);
            }

        }catch (IOException e) {
        }
    }

    /**
     * Clear the solution values obtained from the last set of tuning to obtain new values with the new parameter value
     */
    public static void clearSolValues(){
        try {
            PrintWriter pw = new PrintWriter("solutionValues.txt");
            pw.close();
        }catch (Exception e){}
    }

    /**
     * Reads the solution values obtained by tuning
     * @return
     */
    public static double readSolutionValue(){
        // The name of the file to open.
        String fileName = "solutionValues.txt";
        double[] values = new double[RUNNUM];

        String line = null;
        double sum = 0;

        try {
            FileReader fileReader =
                    new FileReader(fileName);

            BufferedReader bufferedReader =
                    new BufferedReader(fileReader);

            int i = 0;
            while((line = bufferedReader.readLine()) != null) {
                double val = Double.parseDouble(line);
                if(val <= lowestValue){
                    isLowest = true;
                    lowestValue = val;
                }

                values[i] = val;
                sum+= val;
                i++;
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

        return sum;
    }

    /**
     * Read the parameter value that is being tuned from the file
     * @return
     */
    public static double readValue(){
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
}
