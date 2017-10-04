package xrun;

import java.awt.Button;
import java.awt.EventQueue;
import java.awt.Label;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

import org.json.JSONObject;

/**
 * 
 * The first implementation to test the concept.
 */
@Deprecated
public class CalcDistGUI {

  private JFrame frame;
  private JTextField textFieldInput;
  private JTextField textFieldSpeed;
  private JTextField textFieldInterval;
  
  private File currentDir;
  private JTextField textFieldSplit;
  
  private static final double DEFAULT_RUNNING = 9.0; // km/h
  private static final double DEFAULT_INTERVAL = 100; // meters

  /**
   * Launch the application. Rename to use.
   */
  public static void xmain(String[] args) {
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        try {
          CalcDistGUI window = new CalcDistGUI();
          window.frame.setVisible(true);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  /**
   * Create the application.
   */
  public CalcDistGUI() {
    initialize();
  }

  /**
   * Initialize the contents of the frame.
   */
  private void initialize() {
    frame = new JFrame();
    frame.setBounds(100, 100, 450, 322);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.getContentPane().setLayout(null);
    frame.setTitle("Calc dist GUI");
    
    JLabel lblInput = new JLabel("Input");
    lblInput.setBounds(20, 25, 46, 14);
    frame.getContentPane().add(lblInput);
    
    textFieldInput = new JTextField();
    textFieldInput.setBounds(20, 50, 234, 20);
    frame.getContentPane().add(textFieldInput);
    textFieldInput.setColumns(10);
    
    final FileFilter ff = new FileFilter(){
      public boolean accept(File f){
          if(f.isDirectory()) return true;
          else if(f.getName().endsWith(".gpx")) return true;
              else return false;
      }
      public String getDescription(){
          return "GPX files";
      }
  };
    
    Button button = new Button("Browse");
    button.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        if (currentDir != null) {
          chooser.setCurrentDirectory(currentDir);
        }
        chooser.removeChoosableFileFilter(chooser.getAcceptAllFileFilter());
        chooser.setFileFilter(ff);
        int returnVal = chooser.showOpenDialog(frame);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
          textFieldInput.setText(chooser.getSelectedFile().getAbsolutePath());
          currentDir = chooser.getSelectedFile().getParentFile();
        }
      }
    });
    button.setBounds(263, 50, 70, 22);
    frame.getContentPane().add(button);
    
    Label label = new Label("Min running speed(km/h)");
    label.setBounds(20, 87, 142, 22);
    frame.getContentPane().add(label);
    
    textFieldSpeed = new JTextField();
    textFieldSpeed.setBounds(168, 89, 86, 20);
    textFieldSpeed.setText("9.0");
    frame.getContentPane().add(textFieldSpeed);
    textFieldSpeed.setColumns(10);
    
    JLabel lblIntervalm = new JLabel("Interval(m)");
    lblIntervalm.setBounds(20, 126, 62, 14);
    frame.getContentPane().add(lblIntervalm);
    
    textFieldInterval = new JTextField();
    textFieldInterval.setBounds(168, 123, 86, 20);
    textFieldInterval.setText("100");
    frame.getContentPane().add(textFieldInterval);
    textFieldInterval.setColumns(10);
    
    JButton btnCalculate = new JButton("Calculate");
    btnCalculate.setBounds(20, 200, 89, 23);
    frame.getContentPane().add(btnCalculate);
    
    JLabel lblSplitkm = new JLabel("Split(km)");
    lblSplitkm.setBounds(20, 163, 66, 14);
    frame.getContentPane().add(lblSplitkm);
    
    textFieldSplit = new JTextField();
    textFieldSplit.setBounds(168, 160, 86, 20);
    frame.getContentPane().add(textFieldSplit);
    textFieldSplit.setColumns(10);
    textFieldSplit.setText("1.0");
    btnCalculate.addActionListener(new ActionListener() {
      
      @Override
      public void actionPerformed(ActionEvent e) {
        try {
          String filePath = textFieldInput.getText();
          if (filePath == null || filePath.length() == 0) {
            throw new IllegalArgumentException("Input file not specified");
          }
          double minR = DEFAULT_RUNNING;
          double intrv = DEFAULT_INTERVAL;
          double splitM = 1000.0;
          try {
            minR = new Double(textFieldSpeed.getText()).doubleValue();
          } catch (Exception ignore) {
            // silent catch
          }
          try {
            intrv = new Double(textFieldInterval.getText()).doubleValue();
          } catch (Exception ignore) {
            // silent catch
          }
          try {
            splitM = new Double(textFieldSplit.getText()).doubleValue();
          } catch (Exception ignore) {
            // silent catch
          }
          String result = CalcDist.run(new File(filePath), minR,
              intrv, splitM, new JSONObject());
          JOptionPane.showMessageDialog(frame,
              result,
              "Results",
              JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception exc) {
          JOptionPane.showMessageDialog(frame,
              exc.getMessage(),
              "Invalid arguments",
              JOptionPane.ERROR_MESSAGE);
        }
      }
    });
  }
}
