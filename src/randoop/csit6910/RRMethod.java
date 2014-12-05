package randoop.csit6910;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple data structure for store pair values <parameters, output> from method execution
 * */
public class RRMethod {

  private List<Object[][]> inputs;
  private List<Object[]> output;
  public List<Class<?>> parameterTypes;
  public Class<?> returnType;
  public String declaringClassName;
  public String methodName;
  public boolean isStatic;
  
  // helper to generate JUnit test
  public String codeConstructorName;
  public String codeConstructorString;
  
  public RRMethod() {
    parameterTypes = new ArrayList<Class<?>>();
    inputs = new ArrayList<Object[][]>();
    output = new ArrayList<Object[]>();
  }

  /**
   * @return return number of rows, which are (parameters, return value)
   * */
  public int getRowCount() {
    if (inputs.size() != output.size())
      throw new IndexOutOfBoundsException("parameters index is out of sync with return value");
    return output.size();
  }

  public boolean containsResult(Object[] res) {
    return output.contains(res);
  }

  public boolean containsParameters(Object[][] p) {
    return inputs.contains(p);
  }
  
  /**
   * @return concatenation of declaring class and method name
   * */
  public String getCanonicalName() {
    return declaringClassName + "." + methodName;
  }
  
  public Object[] getReturnValue(int index) {
    if (index < 0 || index > output.size()-1)
      throw new IndexOutOfBoundsException();
    return output.get(index);
  }

  public Object[][] getParameterValues(int index) {
    if (index < 0 || index > inputs.size()-1)
      throw new IndexOutOfBoundsException();
    return inputs.get(index);
  }

  public void setExecutionResult(Object[] result, Object[][] parameters) {
    this.inputs.add(parameters);
    this.output.add(result);
  }

  public void setParameterTypes(Class<?>[] c) {
    if (c != null && c.length > 0) {
      for (int i = 0; i < c.length; ++i)
        parameterTypes.add(c[i]);
    }
  }
  
  /**
   * Debug: Print method information such as method name, return type, parameter types, and list values.
   * */
  public void printInfo() {
    int columnCount = parameterTypes.size();
    int rowCount = inputs.size();
    int arraySize;
    
    try {
      System.out.println("-------------------------------------------------------------");
      System.out.println("Method         : " + getCanonicalName());
      System.out.println("Return         : " + returnType.getCanonicalName());
      System.out.print  ("ParameterTypes : ");
      for (Class<?> p : parameterTypes) {
        System.out.print(p.getCanonicalName() + "; ");
      }

      System.out.println();
      System.out.println("Inputs >> Result :");
      for (int i = 0; i < rowCount; ++i) {
        Object[][] obj = inputs.get(i);
        for (int j = 0; j < columnCount; ++j) {
          if (j > 0) System.out.print(", ");
          // (1) handle one-dimension array
          // (2) handle single value
          if (obj[j].length > 1) {
            System.out.print("{");
            arraySize = obj[j].length;
            for (int k = 0; k < arraySize; ++k) {
              if (k > 0) System.out.print(", ");
              System.out.print(obj[j][k].toString());
            }
            System.out.print("}");
          } else {
            System.out.print(obj[j][0].toString());
          }
        }

        Object[] res = output.get(i);
        arraySize = res.length;
        // (1) handle one-dimension array
        // (2) handle single value
        if (arraySize > 1) {
          System.out.print(" >> {");
          for (int k = 0; k < arraySize; ++k) {
            if (k > 0) System.out.print(", ");
            System.out.print(res[k] == null? "null" : res[k].toString());
          }
          System.out.println("}");
        } else {
          System.out.println(" >> " + (res[0] == null? "null" : res[0].toString()));
        }
      }
    } catch (Exception ex) { // just in case
      System.out.println("PrintInfo: " + ex.getMessage());
      ex.printStackTrace();
    }
  }
}
