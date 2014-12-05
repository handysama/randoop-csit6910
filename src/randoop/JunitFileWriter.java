package randoop;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.emory.mathcs.backport.java.util.Arrays;
import randoop.util.CollectionsExt;
import randoop.util.Log;
import randoop.experimental.SequencePrettyPrinter;
import randoop.main.GenInputsAbstract;

/**
 * Outputs a collection of sequences as Java files, using the JUnit framework, with one method per sequence.
 */
public class JunitFileWriter {

  // The class of the main JUnit suite, and the prefix of the subsuite names.
  public String junitDriverClassName;

   // The package name of the main JUnit suite
  public String packageName;

  // The directory where the JUnit files should be written to.
  private String dirName;

  public static boolean includeParseableString = false;

  private int testsPerFile;

  private Map<String, List<List<ExecutableSequence>>> createdSequencesAndClasses = new LinkedHashMap<String, List<List<ExecutableSequence>>>();
  
  public JunitFileWriter(String junitDirName, String packageName, String junitDriverClassName, int testsPerFile) {
    this.dirName = junitDirName;
    this.packageName = packageName;
    this.junitDriverClassName = junitDriverClassName;
    this.testsPerFile = testsPerFile;
  }
  

  public static File createJunitTestFile(String junitOutputDir, String packageName, ExecutableSequence es, String className) {
    JunitFileWriter writer = new JunitFileWriter(junitOutputDir, packageName, "dummy", 1);
    writer.createOutputDir();
    return writer.writeSubSuite(Collections.singletonList(es), className);
  }
  
  /** Creates Junit tests for the faults.
   * Output is a set of .java files.
   */
  public List<File> createJunitTestFiles(List<ExecutableSequence> sequences, String junitTestsClassName) {
    if (sequences.size() == 0) {
      System.out.println("No tests were created. No JUnit class created.");
      return new ArrayList<File>();
    }

    createOutputDir();

    List<File> ret = new ArrayList<File>();
    List<List<ExecutableSequence>> subSuites = CollectionsExt.<ExecutableSequence>chunkUp(new ArrayList<ExecutableSequence> (sequences), testsPerFile);
    // Original:
    //for (int i = 0 ; i < subSuites.size() ; i++) {
    //  ret.add(writeSubSuite(subSuites.get(i), junitTestsClassName + i));
    //}

    /** csit6910: modified here */
    int subSuiteSize = subSuites.size();
    for (int i = 0; i < subSuiteSize; ++i) {
      if (GenInputsAbstract.junit_write_default_test) {
        ret.add(writeSubSuite(subSuites.get(i), junitTestsClassName + i));
      }
      if (GenInputsAbstract.jpf_write_test_driver) {
        writeDriverJPF(subSuites.get(i), junitTestsClassName + i);
      }
      if (GenInputsAbstract.janala_write_test_driver) {
        writeDriverJanala(subSuites.get(i), junitTestsClassName + i);
      }
    }
    
    StringBuilder junitTestSuiteNames = new StringBuilder();
    
    if (GenInputsAbstract.junit_write_theory_test) {
      ret.add(writeSubTheory(subSuites.get(0)));
    }

    if (GenInputsAbstract.junit_write_parameterized_test) {
      ret.addAll(writeSubParameterized(junitTestSuiteNames));
      if (junitTestSuiteNames.length() > 0) {
        writeDriverParameterizedSuite(getDir(), packageName, "ParameterizedTest", 
            Arrays.asList(junitTestSuiteNames.toString().split(";")));
      }
    }

    createdSequencesAndClasses.put(junitTestsClassName, subSuites);
    return ret;
  }

  private void createOutputDir() {
    File dir = getDir();
    if (!dir.exists()) {
      boolean success = dir.mkdirs();
      if (!success) {
        throw new Error("Unable to create directory: " + dir.getAbsolutePath());
      }
    }
  }

  /** Creates Junit tests for the faults.
   * Output is a set of .java files.
   *
   * the default junit class name is the driver class name + index
   */
  public List<File> createJunitTestFiles(List<ExecutableSequence> sequences) {
    return createJunitTestFiles(sequences, junitDriverClassName);
  }

  /** create both the test files and the drivers for convenience **/
  public List<File> createJunitFiles(List<ExecutableSequence> sequences, List<Class<?>> allClasses) {
    List<File> ret = new ArrayList<File>();
    ret.addAll(createJunitTestFiles(sequences));
    ret.add(writeDriverFile(allClasses));
    return ret;
  }

  /** create both the test files and the drivers for convinience **/
  public List<File> createJunitFiles(List<ExecutableSequence> sequences) {
    List<File> ret = new ArrayList<File>();
    ret.addAll(createJunitTestFiles(sequences));
    ret.add(writeDriverFile());
    return ret;
  }


  private File writeSubSuite(List<ExecutableSequence> sequencesForOneFile, String junitTestsClassName) {
    if (GenInputsAbstract.pretty_print) {
      SequencePrettyPrinter printer = new SequencePrettyPrinter(sequencesForOneFile, packageName, junitTestsClassName);
      return printer.createFile(getDir().getAbsolutePath());
    }
	  
    String className = junitTestsClassName;
    File file = new File(getDir(), className + ".java");
    PrintStream out = createTextOutputStream(file);

    try{
      outputPackageName(out, packageName);
      out.println();
      out.println("import junit.framework.*;");
      out.println();
      out.println("public class " + className + " extends TestCase {");
      out.println();
      out.println("  public static boolean debug = false;");
      out.println();
      int testCounter = 1;
      for (ExecutableSequence s : sequencesForOneFile) {
        if (includeParseableString) {
          out.println("/*");
          out.println(s.sequence.toString());
          out.println("*/");
        }
        out.println("  public void test" + testCounter++ + "() throws Throwable {");
        out.println();
        // Replaced this printf by the below to avoid a dependence on Java
        // 5 -- printf was added to the PrintStream class only in J2SE 5.0.
        // out.println(indent("if (debug) System.out.printf(\"%n" + className + ".test" + (testCounter-1) + "\");"));
        out.println(indent("if (debug) { System.out.println(); System.out.print(\"" + className + ".test" + (testCounter-1) + "\"); }"));
        out.println();
        out.println(indent(s.toCodeString()));
        out.println("  }");
        out.println();
      }
      out.println("}");
    } finally {
      if (out != null)
        out.close();
    }

    return file;
  }
  
  // TODO document and move to util directory.
  public static String indent(String codeString) {
    StringBuilder indented = new StringBuilder();
    String[] lines = codeString.split(Globals.lineSep);
    for (String line : lines) {
      indented.append("    " + line + Globals.lineSep);
    }
    return indented.toString();
  }

  private static void outputPackageName(PrintStream out, String packageName) {
    boolean isDefaultPackage= packageName.length() == 0;
    if (!isDefaultPackage)
      out.println("package " + packageName + ";");
  }

  public File writeDriverFile() {
    return writeDriverFile(junitDriverClassName);
  }

  public File writeDriverFile(List<Class<?>> allClasses) {
    return writeDriverFile(junitDriverClassName);
  }
  /**
   * Creates Junit tests for the faults.
   * Output is a set of .java files.
   */
  public File writeDriverFile(String driverClassName) {
    return writeDriverFile(getDir(), packageName, driverClassName, getJunitTestSuiteNames());
  }
  
  public List<String> getJunitTestSuiteNames() {
    List<String> junitTestSuites = new LinkedList<String>();
    for(String junitTestsClassName : createdSequencesAndClasses.keySet()) {
      int numSubSuites = createdSequencesAndClasses.get(junitTestsClassName).size();
      for (int i = 0; i < numSubSuites; i++) {
        junitTestSuites.add(junitTestsClassName + i);
      }
    } 
    return junitTestSuites;
  }
  
  public static File writeDriverFile(File dir, String packageName, String driverClassName,
      List<String> junitTestSuiteNames) {
    File file = new File(dir, driverClassName + ".java");
    PrintStream out = createTextOutputStream(file);
    try {
      outputPackageName(out, packageName);
      out.println("import junit.framework.*;");
      out.println("import junit.textui.*;");
      out.println("");
      out.println("public class " + driverClassName + " extends TestCase {");
      out.println("");
      out.println("  public static void main(String[] args) {");
      if (GenInputsAbstract.init_routine != null)
        out.println ("    " + GenInputsAbstract.init_routine + "();");

      out.println("    TestRunner runner = new TestRunner();");
      out.println("    TestResult result = runner.doRun(suite(), false);");
      out.println("    if (! result.wasSuccessful()) {");
      out.println("      System.exit(1);");
      out.println("    }");
      out.println("  }");
      out.println("");
      out.println("  public " + driverClassName + "(String name) {");
      out.println("    super(name);");
      out.println("  }");
      out.println("");
      out.println("  public static Test suite() {");
      out.println("    TestSuite result = new TestSuite();");
      for(String junitTestsClassName : junitTestSuiteNames) {
        out.println("    result.addTest(new TestSuite(" + junitTestsClassName + ".class));");
      }
      out.println("    return result;");
      out.println("  }");
      out.println("");
      out.println("}");
    } finally {
      if (out != null)
        out.close();
    }
    return file;
  }

  public File getDir() {
    File dir = null;
    if (dirName == null || dirName.length() == 0)
      dir = new File(System.getProperty("user.dir"));
    else
      dir = new File(dirName);
    if (packageName == null)
      return dir;
    packageName = packageName.trim(); // Just in case.
    if (packageName.length() == 0)
      return dir;
    String[] split = packageName.split("\\.");
    for (String s : split) {
      dir = new File(dir, s);
    }
    return dir;
  }

  private static PrintStream createTextOutputStream(File file) {
    try {
      return new PrintStream(file);
    } catch (IOException e) {
      Log.out.println("Exception thrown while creating text print stream:" + file.getName());
      e.printStackTrace();
      System.exit(1);
      throw new Error("This can't happen");
    }
  }


  /** csit6910: write template theory test */
  private File writeSubTheory(List<ExecutableSequence> sequencesForOneFile) {
    String className = "TheoryTest";
    File file = new File(getDir(), className + ".java");
    PrintStream out = createTextOutputStream(file);

    //GenInputsAbstract.long_format = true;
    List<Sequence> listSeq = ExecutableSequence.getSequences(sequencesForOneFile);
    java.util.HashSet<Class<?>> hsClass = new java.util.HashSet<Class<?>>();
    Map<Class<?>,String> ListVar = new java.util.HashMap<Class<?>,String>();

    for (Sequence seq: listSeq)
      for (int j = 0; j < seq.size(); ++j)
        hsClass.addAll(seq.getStatementKind(j).getInputTypes());

    try {
      outputPackageName(out, packageName);
      out.println();
      out.println("import static org.junit.Assume.*;");
      out.println("import static org.hamcrest.CoreMatchers.*;");
      out.println("import static org.junit.matchers.JUnitMatchers.*;");
      out.println();
      out.println("import junit.framework.*;");
      out.println("import org.junit.runner.RunWith;");
      out.println("import org.junit.experimental.theories.DataPoint;");
      out.println("import org.junit.experimental.theories.DataPoints;");
      out.println("import org.junit.experimental.theories.Theory;");
      out.println("import org.junit.experimental.theories.Theories;");
      out.println();
      out.println("@RunWith(Theories.class)");
      out.println("public class " + className + " extends TestCase {");
      out.println();
      out.println("  public static boolean debug = false;");
      out.println();

      /** Generating data point and data points */
      int varCounter = 1;
      for (Class<?> c : hsClass) {
        String classname = c.getCanonicalName();
        String varname = "RVar" + (varCounter++);
        //String val = "";

        if (randoop.util.PrimitiveTypes.isBoxedOrPrimitiveOrStringType(c)) {
          //val = randoop.csit6910.ParameterizedTest.toCodeString(randoop.csit6910.ParameterizedTest.genPrimitiveOrString(c), c);
          String valList = randoop.csit6910.ParameterizedTest.genListPrimitiveOrString(c, 10);
          varname = "List" + varname;
          out.println("  @DataPoints");
          out.println("  public static " + classname + "[] " + varname + " () {");
          out.println("    return new " + classname + "[] {");
          out.println("      " + valList);
          out.println("    };");
          out.println("  }");
          out.println(); 
        }
//        else {
//          Variable var = null;
//          for (Sequence seq : listSeq) {
//            var = seq.randomVariableForType(c, randoop.util.Reflection.Match.EXACT_TYPE);
//            if (var != null) {
//              StringBuilder b = new StringBuilder();
//              List<Variable> inputVars = new ArrayList<Variable>();
//              for (Class<?> clazz : var.getDeclaringStatement().getInputTypes()) {
//                inputVars.add(seq.randomVariableForType(clazz, randoop.util.Reflection.Match.EXACT_TYPE));
//              }
//              val = var.toString();
//              out.println("  //" + var.getDeclaringStatement().toString());
//              var.getDeclaringStatement().appendCode(var, inputVars, b);
//              out.println("  public static " + b);
//              break;
//            }
//          }
//
//          if (var == null) {
//            val = "null";
//          }
//          out.println("  @DataPoint");
//          out.println("  public static " + classname + " " + varname + " = " + val + ";");
//          out.println();
//        }
        // record generated variable
        ListVar.put(c, varname);
      }

      int testCounter = 1;
      for (ExecutableSequence s : sequencesForOneFile) {
        if (includeParseableString) {
          out.println("/*");
          out.println(s.sequence.toString());
          out.println("*/");
        }

        java.util.Iterator<java.util.Map.Entry<Class<?>, String>> it = ListVar.entrySet().iterator();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (it.hasNext()) {
          if (i++ > 0) sb.append(", ");
          Map.Entry<Class<?>, String> pairs = (Map.Entry<Class<?>, String>) it.next();
          sb.append(pairs.getKey().getSimpleName() + " ArgVar" + i + Globals.lineSep);
        }
        
        out.println("/*");
        out.println(sb.toString());
        out.println("*/");
        
        out.println("  @Theory");
        out.println("  public void test" + (testCounter++) + "() throws Throwable {");
        out.println(indent("if (debug) { System.out.println(); System.out.print(\"" + className + ".test" + (testCounter-1) + "\"); }"));
        out.println();
        //out.println(indent(s.toCodeString()));
        out.println("  }");
        out.println();

        /** only create 1 empty test method for template  */
        break;
      }
      out.println("}");
    } finally {
      if (out != null)
        out.close();
    }
    return file;
  }

  /** csit6910: write template parameterized test */
  private List<File> writeSubParameterized(StringBuilder junitTestSuiteNames) {
    List<File> files = new ArrayList<File>();
    randoop.csit6910.ParameterizedTest params = 
        new randoop.csit6910.ParameterizedTest(
            GenInputsAbstract.classlist,
            GenInputsAbstract.methodlist,
            GenInputsAbstract.testclass,
            GenInputsAbstract.omitmethods
        );
    params.generate();
    List<randoop.csit6910.RRMethod> methods = params.getMethods();
    int methodCounter = 1;
    
    for (randoop.csit6910.RRMethod method : methods) {
      if (method.getRowCount() == 0) {
        method.printInfo();
        continue;
      }
      
      String className = "ParameterizedTest"+ (methodCounter++) + "_" + method.methodName;
      File file = new File(getDir(), className + ".java");
      PrintStream out = createTextOutputStream(file);
      
      if (junitTestSuiteNames != null) {
        junitTestSuiteNames.append(className + ";");
      }

      try{
        outputPackageName(out, packageName);
        out.println();
        out.println("import static org.junit.Assert.assertArrayEquals;");
        out.println("import java.util.Collection;");
        out.println("import java.util.Arrays;");
        out.println();
        out.println("import junit.framework.*;");
        out.println("import org.junit.Test;");
        out.println("import org.junit.runner.RunWith;");
        out.println("import org.junit.runners.Parameterized;");
        out.println("import org.junit.runners.Parameterized.Parameter;");
        out.println("import org.junit.runners.Parameterized.Parameters;");
        out.println();
        out.println("/* Parameterized Test for " + method.getCanonicalName() + " */");
        out.println();
        out.println("@RunWith(Parameterized.class)");
        out.println("public class " + className + " extends TestCase {");
        out.println();
        out.println("  public static boolean debug = false;");
        out.println();
        out.println("  @Parameters");
        out.println("  public static Collection<Object[]> data() {");
        out.println("    return Arrays.asList(new Object[][] {");
        
        StringBuilder b = new StringBuilder();
        for (int j = 0 ; j < method.getRowCount() ; ++j) {
          b.append (j > 0 ? "     ,{" : "      {");
          
          Object[][] parameters = method.getParameterValues(j);
          Object[] retval = method.getReturnValue(j);
          
          for (int k = 0; k < parameters.length; ++k) {
            if (k > 0) b.append (", ");
            if (parameters[k].length > 1) {
              b.append (" new " + method.parameterTypes.get(k).getCanonicalName() +" {");
              for (int x = 0; x < parameters[k].length; ++x) {
                if (x > 0) b.append (", ");
                b.append (randoop.csit6910.ParameterizedTest.toCodeString(parameters[k][x], 
                    method.parameterTypes.get(k).getComponentType()));
              }
              b.append ("}");
            } else {
              b.append (randoop.csit6910.ParameterizedTest.toCodeString(parameters[k][0], method.parameterTypes.get(k)));
            }
          }
          
          if (retval.length > 1) {
            b.append (", new " + method.returnType.getCanonicalName() +" {");
            for (int x = 0; x < retval.length; ++x) {
              if (x > 0) b.append (", ");
              b.append (randoop.csit6910.ParameterizedTest.toCodeString(retval[x], 
                  method.returnType.getComponentType()));
            }
            b.append ("} }" + Globals.lineSep);
          } else {
            b.append (", " + randoop.csit6910.ParameterizedTest.toCodeString(retval[0], method.returnType) + "}" + Globals.lineSep);
          }
        }
        out.print(b.toString());
        out.println("    });");
        out.println("  }");
        out.println();

        b = new StringBuilder();
        for (int j = 0; j < method.parameterTypes.size(); ++j) {
          if (j > 0) b.append (", ");
          b.append ("fInput" + (j+1));
          out.println("  @Parameter(value = " + j + ")");
          out.println("  public " + method.parameterTypes.get(j).getCanonicalName() +" fInput" + (j+1) + ";");
          out.println();
        }
        out.println("  @Parameter(value = " + method.parameterTypes.size() + ")");
        out.println("  public " + method.returnType.getCanonicalName() + " fExpected;");
        out.println();

        /** Alternative for initialize through Constructor */
//        out.println("  public " + className + " () {");
//        out.println();
//        out.println("  }");
//        out.println();

        int testCounter = 1;
        out.println("  @Test");
        out.println("  public void test" + (testCounter++) + "() throws Throwable {");
        out.println(indent("if (debug) { System.out.println(); System.out.print(\"" + className + ".test" + (testCounter-1) + "\"); }"));

        String assertString;
        if (method.returnType.isArray()) {
          assertString = "assertArrayEquals";
        } else {
          assertString = "assertEquals";
        }

        if (!method.isStatic) {
          out.println(indent(method.codeConstructorString));
          out.println("    " + assertString + "(fExpected, " + method.codeConstructorName + "." + 
                        method.methodName + "(" + b.toString() + "));");
        } else {
          out.println("    " + assertString + "(fExpected, " + method.getCanonicalName() + "(" + b.toString() + "));");   
        }

        out.println("  }");
        out.println();
        out.println("}");
      } finally {
        if (out != null)
          out.close();
      } 
      files.add(file);
    }
    return files;
  }

  /** csit6910: write parameterized test suite driver */
  public static File writeDriverParameterizedSuite(File dir, String packageName, String driverClassName,
      List<String> junitTestSuiteNames) {
    File file = new File(dir, driverClassName + ".java");
    PrintStream out = createTextOutputStream(file);
    try {
      outputPackageName(out, packageName);
      out.println();
      out.println("import junit.framework.*;");
      out.println("import org.junit.runner.RunWith;");
      out.println("import org.junit.runners.Suite;");
      out.println("import org.junit.runners.Suite.SuiteClasses;");
      out.println();
      out.println("@RunWith(Suite.class)");
      out.println("@SuiteClasses ({");
      for(int i = 0; i< junitTestSuiteNames.size(); ++i) {
        if (i > 0) out.println(",");
        out.print("  "+junitTestSuiteNames.get(i) + ".class");
      }
      out.println();
      out.println("})");
      out.println("public class " + driverClassName + " extends TestCase {");
      out.println();
      out.println("  public static void main(String[] args) {");
      out.println("    // Intentionally empty");
      out.println("  }");
      out.println();
      out.println("}");
    } finally {
      if (out != null)
        out.close();
    }
    return file;
  }

  /** csit6910: write JPF test driver (Experimental) */
  private File writeDriverJPF(List<ExecutableSequence> sequencesForOneFile, String junitTestsClassName) {
    String className = "JPF" + junitTestsClassName;
    String projectName = getProjectName(this.dirName);
    File file = new File(getDir(), className + ".java");
    PrintStream out = createTextOutputStream(file);

    //GenInputsAbstract.long_format = true;
    writePropertiesJPF(projectName);

    try {
      outputPackageName(out, packageName);
      out.println();
      out.println("import org.junit.Test;");
      //out.println("import gov.nasa.jpf.JPF;");
      //out.println("import gov.nasa.jpf.JPFException;");
      out.println("import gov.nasa.jpf.Config;");
      //out.println("import gov.nasa.jpf.JPFConfigException;");
      out.println("import gov.nasa.jpf.util.test.TestJPF;");
      out.println("import gov.nasa.jpf.annotation.JPFConfig;");
      out.println("import gov.nasa.jpf.annotation.FilterField;");
      out.println("import gov.nasa.jpf.vm.Verify;");
      out.println("import gov.nasa.jpf.symbc.Debug;");
      out.println("import gov.nasa.jpf.symbc.Symbolic;");
      out.println("import gov.nasa.jpf.symbc.Concrete;");
      out.println("import gov.nasa.jpf.symbc.numeric.Comparator;");
      out.println("import gov.nasa.jpf.symbc.numeric.PathCondition;");
      out.println("import gov.nasa.jpf.symbc.numeric.SymbolicInteger;");
      out.println("import gov.nasa.jpf.symbc.SymbolicInstructionFactory;");
      //out.println("import gov.nasa.jpf.symbc.SymbolicListener;");
      //out.println("import gov.nasa.jpf.JPFListener;");
      //out.println("import gov.nasa.jpf.search.SearchListener;");
      out.println();
      out.println("public class " + className + " extends TestJPF {");
      out.println();
      out.println("  public static boolean debug = false;");
      out.println();
      out.println("  String[] options = {");
      // TODO: inject method under test.. is it necessary?
//      out.println("    \"+symbolic.method=method(sym#sym)\",");
      out.println("    \"+jvm.insn_factory.class=gov.nasa.jpf.symbc.SymbolicInstructionFactory\",");
      out.println("    \"+symbolic.dp=choco\",");
      out.println("    \"+symbolic.string_dp=automata\",");
      out.println("    \"+symbolic.string_dp_timeout_ms=3000\",");
      out.println("    \"+search.depth_limit=10\",");
      out.println("    \"+listener=gov.nasa.jpf.symbc.SymbolicListener\",");
      out.println("    \"+listener=gov.nasa.jpf.symbc.sequences.SymbolicSequenceListener\"");

      // Concolic Walk parameters
//    out.println("    \"+symbolic.concolic=true\",");
//    out.println("    \"+symbolic.heuristic_walk=true\",");
//    out.println("    \"+symbolic.heuristic_walk.iterations=150\",");
//    out.println("    \"+symbolic.heuristic_walk.neighbors=10\",");
//    out.println("    \"+symbolic.heuristic_walk.tabu_multiplier=0.5\",");
//    out.println("    \"+symbolic.heuristic_walk.tabu_min=3\",");
//    out.println("    \"+symbolic.heuristic_walk.bisect=true\",");
      out.println("  };");
      out.println();

      int testCounter = 1;
      for (ExecutableSequence s : sequencesForOneFile) {
        
        // check for feasible test
        // (1) check primitive or string is exists
        List<Variable> v = s.sequence.getAllVariables();
        boolean isExists = false;
        for (int i = 0; i < v.size(); ++i) {
          Class<?> clazz = v.get(i).getType();
          if (randoop.util.PrimitiveTypes.isBoxedOrPrimitiveOrStringType(clazz)) {
            isExists = true;
            break;
          }
        }
        if (!isExists) {
          ++testCounter;
          System.out.println("skip: " + testCounter);
          continue;
        }

        if (includeParseableString) {
          out.println("/*");
          out.println(s.sequence.toString());
          out.println("*/");
        }
        out.println("  @Test");
        out.println("  public void test" + (testCounter++) + "() throws Throwable {");
        out.println("    if (debug) { System.out.println(); System.out.print(\"" + className + ".test" + (testCounter-1) + "\"); }");
        out.println();
        out.println("    if (verifyNoPropertyViolation(options)) {");
        out.print(indent(s.toCodeString(), 6));
        
        // TODO: inject verify to primitive data here..
//        StringBuilder sb = new StringBuilder();
//        Sequence seq = s.sequence;
//        for (int i = 0; i < seq.size(); ++i) {
//          sb.append("      ");
//          seq.printStatement(sb, i);
//        }
//        out.print(sb.toString());

        out.println("    }");
        out.println("  }");
        out.println();
      }
      out.println("}");
    } finally {
      if (out != null)
        out.close();
    }
    return file;
  }

  /** csit6910: write JPF properties for project (Experimental) */
  private File writePropertiesJPF(String projectName) {
    if (projectName == null || projectName.length() == 0) {
      projectName = "MyProject1";
    }
    
    String wdir = System.getProperty("user.dir");
    // don't modify jpf.properties, this filename is constant
    File file = new File(wdir + "\\" + projectName + "\\jpf.properties");
    PrintStream out = createTextOutputStream(file);
    try {
      out.println(projectName + " = ${config_path}");
      out.println();
      out.println("#--- path specifications");
      out.println("#"+ projectName +".native_classpath= \\");
      out.println("#  ${" + projectName + "}/lib");
      out.println();
      out.println(projectName +".classpath = \\");
      out.println("  ${" + projectName + "}/bin; \\");
      out.println("  ${" + projectName + "}/build");
      out.println();
      out.println(projectName +".test_classpath = \\");
      out.println("  ${" + projectName + "}/bin/" + packageName.replace(".", "/"));
      out.println();
      out.println(projectName +".sourcepath = \\");
      out.println("  ${" + projectName + "}/src; \\");
      out.println("  " + getDir().getPath().replace("\\", "/") + ";");
      out.println();
      out.println("#--- other project specific settings");
      out.println("listener.autoload = \\");
      out.println("  ${listener.autoload},javax.annotation.Nonnull");
      out.println();
      out.println("listener.javax.annotation.Nonnull = \\");
      out.println("  gov.nasa.jpf.aprop.listener.NonnullChecker; \\");
      out.println("  gov.nasa.jpf.symbc.sequences.SymbolicSequenceListener");
      out.println();
    } finally {
      if (out != null)
        out.close();
    }
    return file;
  }

  /** csit6910: write janala2 test driver (Experimental) */
  private File writeDriverJanala(List<ExecutableSequence> sequencesForOneFile, String junitTestsClassName) {
    String className = "Janala" + junitTestsClassName;
    File file = new File(getDir(), className + ".java");
    PrintStream out = createTextOutputStream(file);

//    GenInputsAbstract.long_format = false;
//    List<Sequence> listSeq = ExecutableSequence.getSequences(sequencesForOneFile);
//    HashSet<Class<?>> hsClass = new HashSet<Class<?>>();
//    Map<Class<?>,String> ListVar = new HashMap<Class<?>,String>();

    try {
      outputPackageName(out, packageName);
      out.println();
      out.println("import catg.CATG;");
      out.println("import janala.Main;");
      out.println("import janala.utils.DebugAction;");
      out.println("import janala.utils.Debugger;");
      out.println("import junit.framework.*;");
      out.println("import org.junit.Test;");
      out.println("import org.junit.runner.RunWith;");
      out.println();
      out.println("public class " + className + " extends TestCase {");
      out.println();

      int testCounter = 1;
      for (ExecutableSequence s : sequencesForOneFile) {
        if (includeParseableString) {
          out.println("/*");
          out.println(s.sequence.toString());
          out.println("*/");
        }
        out.println("  @Test");
        out.println("  public void test" + (testCounter++) + "() throws Throwable {");
        out.println();
        out.println(indent(s.toCodeString()));
        out.println("  }");
        out.println();
          
      }
      
      out.println("  public static void main (String[] args) {");
      out.println("    "+className +" app = new "+ className+"();");
      out.println();
      out.println();
      out.println("  }");
      out.println();
      out.println("}");
    } finally {
      if (out != null)
        out.close();
    }
    return file;
  }

  /** csit6910: alternative to get project name<br />
   * (dirName - workingDir) and then take first non-empty directory 
   * */
  private String getProjectName(String dirName) {
    String wdir = System.getProperty("user.dir");
    String res = dirName.substring(wdir.length(), dirName.length());
    if (res.length() == 0)
      return "MyProject1";
    
    String[] split = res.split("\\\\");
    int idx = 0;
    while (idx < split.length) {
      if (split[idx].trim().length() > 0)
        break;
      ++idx;
    }
    return split[idx];
  }
  
  /** csit6910: modified indent to allow more configurable space */
  public static String indent(String codeString, int space) {
    int MAX_SPACE = space < 16? space : 16;
    String spacestr = "";
    for(int i = 0; i < MAX_SPACE; ++i)
      spacestr += " ";
    
    StringBuilder indented = new StringBuilder();
    String[] lines = codeString.split(Globals.lineSep);
    
    for (String line : lines) {
      indented.append(spacestr + line + Globals.lineSep);
    }
    return indented.toString();
  }
  
}
