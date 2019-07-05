import java.io.*;
import java.util.ArrayList;
import java.util.function.Consumer;

public class CSVReader {
    static void read(String filename, Consumer<String[]> handler) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            ArrayList<String> words = new ArrayList<>();

            br.readLine();
            while ((line = br.readLine()) != null) {
                handler.accept(line.split(","));
            }
        } catch (FileNotFoundException e) {
            System.out.println("File " + filename + " not found: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Something went wrong: " + e.getMessage());
        }
    }

    static void write(String filename, String line, boolean append) {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename, append))) {
            bw.write(line);
            bw.newLine();
        } catch (FileNotFoundException e) {
            System.out.println("File " + filename + " not found: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("Something went wrong: " + e.getMessage());
        }
    }
}