package randoop.csit6910;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import edu.emory.mathcs.backport.java.util.Arrays;
import randoop.ArrayDeclaration;
import randoop.BugInRandoopException;
import randoop.ExceptionalExecution;
import randoop.ExecutableSequence;
import randoop.ExecutionOutcome;
import randoop.NormalExecution;
import randoop.PrimitiveOrStringOrNullDecl;
import randoop.RConstructor;
import randoop.RMethod;
import randoop.Sequence;
import randoop.StatementKind;
import randoop.Variable;
import randoop.main.GenInputsAbstract;
import randoop.util.ArrayListSimpleList;
import randoop.util.DefaultReflectionFilter;
import randoop.util.Log;
import randoop.util.PrimitiveTypes;
import randoop.util.Reflection;
import randoop.util.SimpleList;
import randoop.util.Util;

/**
 * Generate pairs of inputs and expected result from Method.<br />
 * Supported types are primitive numeric or string.
 * Not supported multi-dimension arrays.
 * */
public final class ParameterizedTest {
  
  private List<StatementKind> model;
  private List<RRMethod> rrmethods;
  private List<String> testclass;
  private String classlist;
  private String methodlist;
  private Pattern omitmethods;

  public ParameterizedTest(String classlist, String methodlist, List<String> testclass, Pattern omitmethods) {
    model = new ArrayList<StatementKind>();
    rrmethods = new ArrayList<RRMethod>();
    this.classlist = classlist;
    this.methodlist = methodlist;
    this.testclass = testclass;
    this.omitmethods = omitmethods;
  }

  /**
   * findClassesFromArgs: copied from GenInputsAbstract because is not visible in here.<br /> 
   **/
  protected List<Class<?>> findClassesFromArgs() {
    List<Class<?>> classes = new ArrayList<Class<?>>();
    try {
      if (classlist != null) {
        File classListingFile = new File(classlist);
        classes.addAll(Reflection.loadClassesFromFile(classListingFile, true));
      }
      classes.addAll(Reflection.loadClassesFromList(testclass, GenInputsAbstract.silently_ignore_bad_class_names));
    } catch (Exception e) {
      String msg = Util.toNColsStr("ERROR while reading list of classes to test: " + e.getMessage(), 70);
      System.out.println(msg);
      System.exit(1);
    }
    return classes;
  }

  /**
   * Create a list of all classes and methods involved to generate.<br />
   * modified from GenTest: handle(), with decoupled between method and constructor
   * @return number of models
   * */
  private int genModels() {
    // Find classes to test.
    if (classlist == null && methodlist == null && testclass.size() == 0) {
      System.out.println("You must specify some classes or methods to test.");
      System.out.println("Use the --classlist, --testclass, or --methodlist options.");
      System.exit(1);
    }

    List<Class<?>> allClasses = findClassesFromArgs();
    
    // Remove private (non-.isVisible) classes and abstract classes
    // and interfaces.
    List<Class<?>> classes = new ArrayList<Class<?>>(allClasses.size());
    for (Class<?> c : allClasses) {
      if (Reflection.isAbstract (c)) {
        System.out.println("Ignoring abstract " + c + " specified via --classlist or --testclass.");
      } else if (!Reflection.isVisible (c)) {
        System.out.println("Ignoring non-visible " + c + " specified via --classlist or --testclass.");
      } else {
        classes.add(c);
      }
    }

    // Make sure each of the classes is visible.  Should really make sure
    // there is at least one visible constructor/factory in each class as well.
    for (Class<?> c : classes) {
      if (!Reflection.isVisible (c)) {
        throw new Error ("Specified class " + c + " is not visible");
      }
    }

    DefaultReflectionFilter reflectionFilter = new DefaultReflectionFilter(omitmethods);
    //List<StatementKind> model = Reflection.getStatements(classes, reflectionFilter);
    model = Reflection.getStatements(classes, reflectionFilter);

    // Always add Object constructor (it's often useful).
    RConstructor objectConstructor = null;
    try {
      objectConstructor = RConstructor.getRConstructor(Object.class.getConstructor());
      if (!model.contains(objectConstructor)) {
        model.add(objectConstructor);
      }

    } catch (Exception e) {
      throw new BugInRandoopException(e); // Should never reach here!
    }

    if (methodlist != null) {
      Set<StatementKind> statements = new LinkedHashSet<StatementKind>();
      try {
        for (Member m : Reflection.loadMethodsAndCtorsFromFile(new File(methodlist))) {
          if (m instanceof Method) {
            if (reflectionFilter.canUse((Method)m)) {
              statements.add(RMethod.getRMethod((Method)m));
            }
          } else {
            assert m instanceof Constructor<?>;
            if (reflectionFilter.canUse((Constructor<?>)m)) {
              statements.add(RConstructor.getRConstructor((Constructor<?>)m));
            }
          }
        }
      } catch (IOException e) {
        System.out.println("Error while reading method list file " + methodlist);
        System.exit(1);
      }
      
      for (StatementKind st : statements) {
        if (!model.contains(st)) {
          model.add(st);
        }
      }
    }

    if (model.size() == 0) {
      System.out.println("There are no methods to test. Exiting.");
      Log.out.println("There are no methods to test. Exiting.");
      System.exit(1);
    }
    
    return model.size();
  }

  /**
   * Capture output from method execution result.<br />
   * This implementation is still naive PUTs, i.e. not smart enough to generate the inputs value.<br />
   * TODO : one may consider scenario where object instantiate from static factory.
   * @return number of methods processed
   * */
  private int captureOutput() {
    // we need to define max attempts in case, we can't reach target parameters
    // temporary, we define 3 times target parameters per files
    int maxAttempts = 3 * GenInputsAbstract.junit_parameters_per_file;

    
    for (StatementKind st : model) {

      // we don't interest in constructor
      if (st instanceof RConstructor)
        continue;
      
      // we don't interest in no-argument parameter method
      if (((RMethod)st).getMethod().getParameterTypes().length == 0)
        continue;

      Method m = ((RMethod)st).getMethod();
      List<Class<?>> inputs = st.getInputTypes();
      Class<?>[] parameterTypes = m.getParameterTypes();
      Class<?> outputType = st.getOutputType();
      Class<?> declaringClass = m.getDeclaringClass();
      StatementKind classConstructor = null;
      int inputsSize = parameterTypes.length;
      boolean isStatic = ((RMethod)st).isStatic();
      boolean isInputTypesValid = true;
      boolean isOutputTypeValid = true;
      boolean constructorCheck = false;

      // If method is not static, they will include declaring class
      // else  we don't need to get the constructor
      // TODO : create mechanism to find suitable constructor only once
      if (!isStatic) {
        for (StatementKind statement : model) {
          if (!constructorCheck && statement instanceof RConstructor) {
            Constructor<?> constructor = ((RConstructor)statement).getConstructor();
            if (constructor.getDeclaringClass().equals(declaringClass)) {
              Class<?>[] cc = constructor.getParameterTypes();
              boolean isOK = true;
              // guarantee all constructor parameters are primitive
              for (int idx = 0; idx < cc.length; ++idx) {
                if (!isPrimitiveOrStringNonVoid(cc[idx]) &&
                    !isPrimitiveOrStringOneDimensionArrayType(cc[idx])
                    ) {
                  isOK = false;
                  break;
                }
              }
              if (isOK) {
                classConstructor = RConstructor.getRConstructor(constructor);
                constructorCheck = true;
                break;
              }
            }
          }
        }

        if (classConstructor == null) {
          // try default empty constructor
          try {
            classConstructor = RConstructor.getRConstructor(declaringClass.getConstructor());
          } catch (NoSuchMethodException e) {
            //e.printStackTrace();
          }
          // Object constructor as last resort
          if (classConstructor == null) {
            try { 
              classConstructor = RConstructor.getRConstructor(Object.class.getConstructor());
            } catch (NoSuchMethodException e) {
              //e.printStackTrace();
            }
          }
        }
      }

      // verify the constructor and I/O data types
      for (int j = 0; j < inputsSize; ++j)
        if (!isPrimitiveOrStringNonVoid(parameterTypes[j]) &&
            !isPrimitiveOrStringOneDimensionArrayType(parameterTypes[j])) {
          isInputTypesValid = false;
          break;
        }
      
      constructorCheck = isStatic? true : constructorCheck;      
      isOutputTypeValid = isPrimitiveOrStringNonVoid(outputType) || 
                          isPrimitiveOrStringOneDimensionArrayType(outputType);

      if (constructorCheck && isInputTypesValid && isOutputTypeValid) {
        StatementKind cMethod = RMethod.getRMethod(m);
        RRMethod method = new RRMethod();
        method.methodName = m.getName();
        method.parameterTypes = Arrays.asList(parameterTypes);
        method.declaringClassName = declaringClass.getCanonicalName();
        method.returnType = outputType;
        method.isStatic = isStatic;
        
        System.out.println("Debug: " + method.methodName);

        for (int count = 0, attempt = 0; count < GenInputsAbstract.junit_parameters_per_file; ++attempt) {
          List<Variable> inputVariables = new ArrayList<Variable>(); // for extend sequence method
          List<Object[]> inputParameters = new ArrayList<Object[]>(); // for RRMethod
          List<Integer> parameterIndex = new ArrayList<Integer>();
          Sequence seq = new Sequence();
//          int constructorIndex = 0;
          
          // there are two cases in here, 
          // (1) we need to construct constructor sequence, when method is not static
          // (2) simply construct primitive declaration for method arguments
          
          for (Class<?> inputType : inputs) {
            if (!isStatic && declaringClass.equals(inputType)) {
              Class<?>[] constructorParameters = ((RConstructor) classConstructor).getConstructor().getParameterTypes();
              List<Integer> pIndex = new ArrayList<Integer>();

              for (Class<?> clazz : constructorParameters) {
                if (isPrimitiveOrStringNonVoid(clazz)) {
                  Object value = genPrimitiveOrString(clazz);
                  Class<?> primitive = PrimitiveTypes.primitiveType(clazz);
                  seq = seq.extend(new PrimitiveOrStringOrNullDecl(primitive, value));
                }
                else if (isPrimitiveOrStringOneDimensionArrayType(clazz)) {
                  Object[] in = genOneDimensionArrayPrimitiveOrString(clazz, 10); // assume array length is 10
                  
                  List<Sequence> newSequence = constructPrimitiveOrStringArray(in, clazz.getComponentType()).toJDKList();
                  newSequence.add(0, seq);
                  seq = Sequence.concatenate(newSequence);
                }
                pIndex.add(seq.size()-1);
              }

              List<Variable> inputConst = new ArrayList<Variable>();
              StringBuilder b = new StringBuilder();
              
              for (int k = 0; k < pIndex.size(); ++k) {
                seq.printStatement(b, pIndex.get(k));
                inputConst.add(seq.getVariable(pIndex.get(k)));
              }

              seq = seq.extend(classConstructor, inputConst);
//              constructorIndex = seq.size()-1;
              parameterIndex.add(seq.size()-1);

              seq.printStatement(b, seq.size() - 1);
              method.codeConstructorName = seq.getLastVariable().getName();
              method.codeConstructorString = b.toString();
            
            } else {
              if (isPrimitiveOrStringOneDimensionArrayType(inputType)) {
                Object[] in = genOneDimensionArrayPrimitiveOrString(inputType, 10); // assume array length is 10
                
                List<Sequence> newSequence = constructPrimitiveOrStringArray(in, inputType.getComponentType()).toJDKList();
                newSequence.add(0, seq);
                seq = Sequence.concatenate(newSequence);
                inputParameters.add(in);
              } else {
                Object value = genPrimitiveOrString(inputType);
                Class<?> primitive = PrimitiveTypes.primitiveType(inputType);
                seq = seq.extend(new PrimitiveOrStringOrNullDecl(primitive, value));
                inputParameters.add(new Object[] {value});
              }
              parameterIndex.add(seq.size()-1);
            }
          }

//          if (!method.isStatic)
//            inputVariables.add(seq.getVariable(constructorIndex));
          
          // Please note that sequence is immutable so we can't capture in the middle Sequence extend iteration
          // and please take care of the subsequence statements
          
          for (int j = 0; j < parameterIndex.size(); ++j) {
            
            inputVariables.add(seq.getVariable(parameterIndex.get(j)));
          }

          // supply the method arguments
          seq = seq.extend(cMethod, inputVariables);

//          System.out.println("Seq size" + seq.size());
//          StringBuilder b = new StringBuilder();
//          for (int j=0; j < seq.size(); ++j) {
//            seq.printStatement(b, j);
//          }
//          System.out.print(b.toString());
          ExecutableSequence es = new ExecutableSequence(seq);
          es.execute(null);

          // Execution result goes here
          ExecutionOutcome resultAt = es.getResult(seq.size() - 1);
          if (resultAt instanceof NormalExecution) {
            Object[][] in = new Object[inputParameters.size()][];
            for (int i = 0; i < inputParameters.size(); ++i) {
              in[i] = inputParameters.get(i);
            }
            
            if (!method.containsParameters(in)) {
              Object retval = ((NormalExecution)resultAt).getRuntimeValue();
              Object[] res;              
              if (retval != null && isPrimitiveOrStringOneDimensionArrayType(retval.getClass())) {
                int length = Array.getLength(retval);
                res = new Object[length];
                for (int i = 0; i < length; ++i) {
                  res[i] = Array.get(retval, i);
                }    
              } else {
                res = new Object[] {retval};
              }
              method.setExecutionResult(res, in);
              ++count;
            }
          } else {
            // Catch exception in here
//            if (resultAt instanceof ExceptionalExecution) {
//              System.out.println(((ExceptionalExecution)resultAt).toString());
//            }
          }

          // limit attempt to avoid infinite loops
          if (attempt >= maxAttempts)
            break;
        }

        method.printInfo();
        rrmethods.add(method);
      }
    }
    return rrmethods.size();
  }

  /**
   * Launcher to invoke generate the PUTs
   * */
  public void generate() {
    if (genModels() > 0)
      captureOutput();
  }

  /**
   * Return list of generated PUTs
   * */
  public List<RRMethod> getMethods() {
    return rrmethods;
  }

  /** 
   * generate random primitive data and string <br />
   * TODO : should more configurable and move to Util class
   * */
  public static Object genPrimitiveOrString(Class<?> clazz) {
    if (!isPrimitiveOrStringNonVoid(clazz)) 
      throw new IllegalArgumentException("genPrimitiveOrString: type have to boxed or primitive or string.");

    java.util.Random rnd = new java.util.Random(System.nanoTime());
    
    String[] collectionString = {
        "", "  abc", "sometext  ", "aaa@mail", "123abc"
    };
    
    if (clazz.equals(int.class) || clazz.equals(Integer.class)) {
      if (randoop.util.Randomness.weighedCoinFlip(0.7))
        return randoop.util.Randomness.nextRandomInt(1000);
      else
        return -1 * randoop.util.Randomness.nextRandomInt(1000);
    }
    else if (clazz.equals(boolean.class) || clazz.equals(Boolean.class)) {
      return randoop.util.Randomness.nextRandomBool();
    }
    else if (clazz.equals(float.class) || clazz.equals(Float.class)) {
       return rnd.nextFloat();
    }
    else if (clazz.equals(double.class) || clazz.equals(Double.class)) {
      return rnd.nextDouble();
    }
    else if (clazz.equals(long.class) || clazz.equals(Long.class)) {
      return rnd.nextLong();
    }
    else if (clazz.equals(short.class) || clazz.equals(Short.class)) {
      return (short) rnd.nextInt();
    }
    else if (clazz.equals(byte.class) || clazz.equals(Byte.class)) {
      return (byte) (rnd.nextInt() % Byte.MAX_VALUE);
    }
    else if (clazz.equals(char.class) || clazz.equals(Character.class)) {
      // random visible character letter or digit
      int codePoint = randoop.util.Randomness.nextRandomInt(128);
      while (!Character.isLetterOrDigit(codePoint)) {
        codePoint = randoop.util.Randomness.nextRandomInt(128);
      }
      return (char) codePoint;
    }
    else if (clazz.equals(String.class)) {
      // random from collection string
      int idx = randoop.util.Randomness.nextRandomInt(collectionString.length-1);
      return collectionString[idx];
    }
    // should never reach here
    return null;
  }

  /** 
   * generate list of primitive numeric data and string.<br />
   * TODO : suppose to moved to Util class.
   * */
  public static String genListPrimitiveOrString(Class<?> c, int count) {
    if (!randoop.util.PrimitiveTypes.isBoxedOrPrimitiveOrStringType(c)) 
      throw new IllegalArgumentException("class have to boxed or primitive or string.");

    StringBuilder res = new StringBuilder();
    for (int i = 0; i < count; ++i) {
      if (i > 0) res.append(", ");
      String s = genPrimitiveOrString(c).toString();
      s = toCodeString(s, c);
      res.append(s);
    }
    return res.toString();
  }

  /** 
   * generate list of primitive numeric data and string.<br />
   * TODO : suppose to moved to Util class.
   * */
  public static Object[] genOneDimensionArrayPrimitiveOrString(Class<?> clazz, int size) {
    if (!isPrimitiveOrStringOneDimensionArrayType(clazz))
      throw new IllegalArgumentException("class have to be one dimension array of primitive or string.");

    Object[] res = new Object[size];
    Class<?> componentType = clazz.getComponentType();
    for (int i = 0; i < size; ++i) {
      res[i] = genPrimitiveOrString(componentType);
    }
    return res;
  }
  
  /**
   * To ensure class is primitive numeric only by excluding void.class<br />
   * TODO : suppose to moved to Util class.
   * */
  public static boolean isPrimitiveOrStringNonVoid(Class<?> clazz) {
    return (PrimitiveTypes.isBoxedOrPrimitiveOrStringType(clazz) && 
        clazz != void.class && clazz != Void.class);
  }

  /**
   * Check given class is one dimension array of primitive or string values.<br />
   * Acknowledgment: copied from Palus API with modification.<br />
   * TODO : suppose to moved to Util class.
   * */
  public static boolean isPrimitiveOrStringOneDimensionArrayType(Class<?> clazz) {
    if(!clazz.isArray()) {
      return false;
    } else {
      Class<?> componentType = clazz.getComponentType();
      if (componentType.isArray()) {
        //multiple dimension array
        return false;
      } else {
        return isPrimitiveOrStringNonVoid(componentType);
      }
    }
  }

  /**
   * Constructs a sequence for constructing a primitive or string type array.<br />
   * Acknowledgment: copied from Palus API with modification.<br />
   * TODO : suppose to moved to Util class.
   * */
  protected SimpleList<Sequence> constructPrimitiveOrStringArray(Object[] array, Class<?> componentType) {
    
    // This is needed because primitiveOrStringOrNullDecl not support Boxed type
    if (!componentType.isPrimitive())
      componentType = PrimitiveTypes.primitiveType(componentType);
    
    assert array != null;
    assert componentType != null;  
    if (isPrimitiveOrStringOneDimensionArrayType(componentType))
      throw new IllegalArgumentException("componentType is not primitive");

    //get the length
    int length = array.length;
    List<Sequence> arrayElements = new LinkedList<Sequence>();
    for (int i = 0; i < length; ++i) {
      Sequence seq = Sequence.create(new PrimitiveOrStringOrNullDecl(componentType, array[i]));
      arrayElements.add(seq);
    }
    
    Sequence s = Sequence.concatenate(arrayElements);
    
    //init the array declaration
    ArrayDeclaration decl = new ArrayDeclaration(componentType, length);
    List<Variable> vars = new LinkedList<Variable>();
    for (int i = 0; i < length; ++i) {
      vars.add(s.getVariable(i));
    }
    s = s.extend(decl, vars);

    //the sequence list to return
    ArrayListSimpleList<Sequence> arrayList = new ArrayListSimpleList<Sequence>();
    arrayList.add(s);
    
    return arrayList;
  }
  
  /** 
   * Return normalized code string representation of the value according to its class.<br />
   * Supported types: String, char, float, long.<br />
   * TODO : suppose to moved to Util class.
   * @param o : value
   * @param c : class of value
   * */
  public static String toCodeString(Object o, Class<?> c) {
    String val;
    if (o == null) {
      val = "null";
    } else {
      val = o.toString(); 
      // TODO : consider what are other escape sequence character
      if (c.equals(String.class))
        val = "\"" + val.replace("\\", "\\\\") + "\""; 
      else if (c.equals(char.class) || c.equals(Character.class))
        val = "'" + val + "'";
      else if (c.equals(float.class) || c.equals(Float.class))
        val = val + "f";
      else if (c.equals(long.class) || c.equals(Long.class))
        val = val + "L";
      else if (c.equals(byte.class) || c.equals(Byte.class))
        val = "(byte)"+ val;
      else if (c.equals(short.class) || c.equals(Short.class))
        val = "(short)"+ val;
    }
    return val;
  }
  
}