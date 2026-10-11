package org.jeecg.common.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jeecg.common.constant.SymbolConstant;

/**
 * @Author Zhang Daihao
 */
public class MyClassLoader extends ClassLoader {
  private static final Logger log = LoggerFactory.getLogger(MyClassLoader.class);

	public static Class getClassByScn(String className) {
		Class myclass = null;
		try {
			myclass = Class.forName(className);
		} catch (ClassNotFoundException e) {
			log.error("operation failed", e);
			throw new RuntimeException(className+" not found!");
		}
		return myclass;
	}

    /**
     * Get fully qualified class name including package
     * @param object
     * @return
     */
	public static String getPackPath(Object object) {
		// Check whether user-supplied parameters are empty
		if (object == null) {
			throw new java.lang.IllegalArgumentException("Parametercannot be empty！");
		}
		// Get fully qualified class name including package
		String clsName = object.getClass().getName();
		return clsName;
	}

	public static String getAppPath(Class cls) {
		// Check whether user-supplied parameters are empty
		if (cls == null) {
			throw new java.lang.IllegalArgumentException("Parametercannot be empty！");
		}
		ClassLoader loader = cls.getClassLoader();
		// Get fully qualified class name including package
		String clsName = cls.getName() + ".class";
		// Get the package where the incoming parameters are located
		Package pack = cls.getPackage();
		String path = "";
		// If it is not an anonymous package, convert the package name into a path
		if (pack != null) {
			String packName = pack.getName();
			String javaSpot="java.";
			String javaxSpot="javax.";
			// Here we simply determine whether it is a Java basic class library to prevent users from passing in JDK built-in class libraries.
			if (packName.startsWith(javaSpot) || packName.startsWith(javaxSpot)) {
				throw new java.lang.IllegalArgumentException("Do not pass system classes!");
			}
			// In the name of the class, remove the package name part and obtain the File name of the class
			clsName = clsName.substring(packName.length() + 1);
			// Determine whether the package name is a simple package name. If so, directly convert the package name into a path.
			if (packName.indexOf(SymbolConstant.SPOT) < 0) {
				path = packName + "/";
			} else {
                // Otherwise, convert the package name into a path according to the components of the package name
				int start = 0, end = 0;
				end = packName.indexOf(".");
				StringBuilder pathBuilder = new StringBuilder();
				while (end != -1) {
                    pathBuilder.append(packName, start, end).append("/");
					start = end + 1;
					end = packName.indexOf(".", start);
				}
				if(oConvertUtils.isNotEmpty(pathBuilder.toString())){
                    path = pathBuilder.toString();
                }
				path = path + packName.substring(start) + "/";
			}
		}
		// Call the getResource method of ClassLoader and pass in the class File name containing path information.
		java.net.URL url = loader.getResource(path + clsName);
		// Get path information from URL object
		String realPath = url.getPath();
		// Remove the protocol name "file:" from the path information
		int pos = realPath.indexOf("file:");
		if (pos > -1) {
			realPath = realPath.substring(pos + 5);
		}
		// Remove the last part of the path information that contains the class file information to get the path where the class is located.
		pos = realPath.indexOf(path + clsName);
		realPath = realPath.substring(0, pos - 1);
		// If the class file is packaged into a JAR or other file, remove the corresponding JAR or other packaging File name
		if (realPath.endsWith(SymbolConstant.EXCLAMATORY_MARK)) {
			realPath = realPath.substring(0, realPath.lastIndexOf("/"));
		}
		/*------------------------------------------------------------  
		 The getResource method of ClassLoader uses utf-8 to encode the path information. When the path  
		  When there are Chinese characters and spaces in it, it will convert these characters, so what we get is often not what we want.  
		  The real path, here, the decode method of URLDecoder is called to decode in order to get the original  
		  Chinese and space paths  
		-------------------------------------------------------------*/
		try {
			realPath = java.net.URLDecoder.decode(realPath, "utf-8");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return realPath;
	}// end of getAppPath definition
}
