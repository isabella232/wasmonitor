/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wavefront.websphere;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.websphere.management.AdminServiceFactory;
import com.ibm.websphere.pmi.stat.WSAverageStatistic;
import com.ibm.websphere.pmi.stat.WSBoundaryStatistic;
import com.ibm.websphere.pmi.stat.WSCountStatistic;
import com.ibm.websphere.pmi.stat.WSDoubleStatistic;
import com.ibm.websphere.pmi.stat.WSRangeStatistic;
import com.ibm.websphere.pmi.stat.WSStatistic;
import com.ibm.websphere.pmi.stat.WSStats;
import com.ibm.websphere.pmi.stat.WSTimeStatistic;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.j2ee.statistics.BoundaryStatistic;
import javax.management.j2ee.statistics.CountStatistic;
import javax.management.j2ee.statistics.RangeStatistic;
import javax.management.j2ee.statistics.Statistic;
import javax.management.j2ee.statistics.Stats;
import javax.management.j2ee.statistics.TimeStatistic;
import javax.management.openmbean.CompositeData;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author glaullon
 */
public class StatsServlet extends DafaultServlet {

  private static final long serialVersionUID = 1L;

  private static final Map<String, List<String>> javamBeans = new HashMap<>();
  private static final List<String> wasStatsmBeans = new ArrayList<>();
  private static final List<String> wasPerfsmBeans = new ArrayList<>();

  static {
    javamBeans.put("java.lang:type=OperatingSystem", Arrays.asList(new String[]{"ProcessCpuLoad", "SystemLoadAverage", "SystemCpuLoad"}));
    javamBeans.put("java.lang:type=Memory", Arrays.asList(new String[]{"HeapMemoryUsage", "NonHeapMemoryUsage", "ObjectPendingFinalizationCount"}));
    javamBeans.put("java.lang:type=GarbageCollector", Arrays.asList(new String[]{"CollectionTime", "CollectionCount"}));
    javamBeans.put("java.lang:type=MemoryPool", Arrays.asList(new String[]{"Usage", "PeakUsage", "CollectionUsage"}));

    wasStatsmBeans.add("WebSphere:type=TransactionService");
    wasStatsmBeans.add("WebSphere:type=ThreadPool");
    wasStatsmBeans.add("WebSphere:type=SessionManager");

    wasPerfsmBeans.add("WebSphere:type=JDBCProvider");
  }

  private static JSONArray getObjectsAttributes(MBeanServerConnection server, Set<ObjectName> objectsNames, List<String> attributes) {
    JSONArray res = new JSONArray();
    for (ObjectName objectName : objectsNames) {
      JSONObject object = new JSONObject();
      object.putAll(getTags(objectName));
      JSONObject metrics = new JSONObject();
      try {
        AttributeList stats = server.getAttributes(objectName, attributes.toArray(new String[attributes.size()]));
        stats.forEach((stat) -> {
          if (stat instanceof Attribute) {
            Attribute attr = (Attribute) stat;
            if (attr.getValue() instanceof CompositeData) {
              metrics.put(attr.getName(), toMap((CompositeData) attr.getValue()));
            } else {
              metrics.put(attr.getName(), attr.getValue());
            }
          } else {
            object.put("error", "Failed to get stat '" + stat.toString() + "' object on " + objectName + " unsuported class '" + stat.getClass() + "'.");
          }
        });
      } catch (ReflectionException | InstanceNotFoundException | IOException ex) {
        JSONObject error = new JSONObject();
        error.put("error", ex.getMessage());
        error.put("stack", getStackTrace(ex));
        object.put("error", error);
      }
      object.put(objectName.getKeyProperty("type"), metrics);
      res.add(object);
    }
    return res;
  }

  private static JSONArray getObjectStats(MBeanServerConnection server, Set<ObjectName> objectsNames) throws IOException, ReflectionException, IntrospectionException, InstanceNotFoundException, MBeanException, AttributeNotFoundException {
    JSONArray res = new JSONArray();
    for (ObjectName objectName : objectsNames) {
      JSONObject object = new JSONObject();
      object.putAll(getTags(objectName));
      JSONObject metrics = new JSONObject();
      Object stats = server.getAttribute(objectName, "stats");
      if ((stats != null) && (stats instanceof Stats)) {
        Stats wsStats = (Stats) stats;
        for (Statistic statistic : wsStats.getStatistics()) {
          metrics.put(statistic.getName(), toJSONObject(statistic));
        }
        object.put(objectName.getKeyProperty("type"), metrics);
        res.add(object);
      }
    }
    return res;
  }

  private static JSONArray getObjectStats(MBeanServerConnection server, ObjectName perf, Set<ObjectName> objectsNames) throws MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException, IOException {
    String[] signature = {"javax.management.ObjectName", "java.lang.Boolean"};
    JSONArray res = new JSONArray();
    for (ObjectName objectName : objectsNames) {
      JSONObject object = new JSONObject();
      object.putAll(getTags(objectName));

      JSONObject metrics = new JSONObject();

      Object[] params = {objectName, Boolean.FALSE};
      Object stats;
      if (perf == null) {
        stats = server.getAttribute(objectName, "stats");
      } else {
        stats = server.invoke(perf, "getStatsObject", params, signature);
        if (stats == null) {
          throw new RuntimeException("Failed to getStatsObject on " + perf + " for object '" + objectName + "'.");
        }
      }

      if (stats instanceof WSStats) {
        WSStats wsStats = (WSStats) stats;
        for (WSStatistic statistic : wsStats.getStatistics()) {
          metrics.put(statistic.getName(), toJSONObject(statistic));
        }
      } else {
        throw new RuntimeException("Failed to get stats object on " + perf + " unsuported class '" + stats.getClass() + "'.");
      }
      object.put(objectName.getKeyProperty("type"), metrics);
      res.add(object);
    }
    return res;
  }

  private static JSONObject toJSONObject(WSStatistic stat) {
    JSONObject stats = new JSONObject();

    if (stat instanceof WSCountStatistic) {
      stats.put("count", ((WSCountStatistic) stat).getCount());
    }

    if (stat instanceof WSAverageStatistic) {
      stats.put("count", ((WSAverageStatistic) stat).getCount());
      stats.put("total", ((WSAverageStatistic) stat).getTotal());
      stats.put("max", ((WSAverageStatistic) stat).getMax());
      stats.put("avg", ((WSAverageStatistic) stat).getMean());
      stats.put("min", ((WSAverageStatistic) stat).getMin());
    }

    if (stat instanceof WSBoundaryStatistic) {
      stats.put("lower", ((WSBoundaryStatistic) stat).getLowerBound());
      stats.put("upper", ((WSBoundaryStatistic) stat).getUpperBound());
    }

    if (stat instanceof WSDoubleStatistic) {
      stats.put("count", ((WSDoubleStatistic) stat).getDouble());
    }

    if (stat instanceof WSRangeStatistic) {
      stats.put("current", ((WSRangeStatistic) stat).getCurrent());
      stats.put("avg", ((WSRangeStatistic) stat).getMean());
    }

    if (stat instanceof WSTimeStatistic) {
      stats.put("time.total", ((WSTimeStatistic) stat).getTotalTime());
      stats.put("time.max", ((WSTimeStatistic) stat).getMaxTime());
      stats.put("time.min", ((WSTimeStatistic) stat).getMinTime());
    }

    stats.put("unit", stat.getUnit());

    return stats;
  }

  private static JSONObject toJSONObject(Statistic stat) {
    JSONObject stats = new JSONObject();

    if (stat instanceof CountStatistic) {
      stats.put("count", ((CountStatistic) stat).getCount());
    }

    if (stat instanceof BoundaryStatistic) {
      stats.put("lower", ((BoundaryStatistic) stat).getLowerBound());
      stats.put("upper", ((BoundaryStatistic) stat).getUpperBound());
    }

    if (stat instanceof RangeStatistic) {
      stats.put("current", ((RangeStatistic) stat).getCurrent());
      stats.put("low", ((RangeStatistic) stat).getLowWaterMark());
      stats.put("high", ((RangeStatistic) stat).getHighWaterMark());
    }

    if (stat instanceof TimeStatistic) {
      stats.put("count", ((TimeStatistic) stat).getCount());
      stats.put("time.total", ((TimeStatistic) stat).getTotalTime());
      stats.put("time.max", ((TimeStatistic) stat).getMaxTime());
      stats.put("time.min", ((TimeStatistic) stat).getMinTime());
    }

    stats.put("unit", stat.getUnit());

    return stats;
  }

  /**
   * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
   *
   * @param request  servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException      if an I/O error occurs
   */
  @Override
  protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    response.setContentType("application/json");

    ObjectName queryName;

    JSONArray res = new JSONArray();

    // JVM Standar metrics
    try {
      MBeanServer server = ManagementFactory.getPlatformMBeanServer();
      for (Map.Entry<String, List<String>> entry : javamBeans.entrySet()) {
        String query = entry.getKey();
        List<String> attributes = entry.getValue();
        queryName = new ObjectName(query + ",*");
        Set<ObjectName> objectsNames = server.queryNames(queryName, null);
        res.addAll(getObjectsAttributes(server, objectsNames, attributes));
      }
    } catch (MalformedObjectNameException ex) {
      JSONObject error = new JSONObject();
      error.put("error", ex.getMessage());
      error.put("stack", getStackTrace(ex));
      res.add(error);
    }

    // WAS Stats based metrics
    try {
      MBeanServerConnection server = AdminServiceFactory.getMBeanFactory().getMBeanServer();
      Set<ObjectName> objectsNames = new HashSet<>();
      for (String ibmStatsmBean : wasStatsmBeans) {
        queryName = new ObjectName(ibmStatsmBean + ",*");
        objectsNames.addAll(server.queryNames(queryName, null));
      }
      res.addAll(getObjectStats(server, objectsNames));
    } catch (IOException | MalformedObjectNameException | ReflectionException | IntrospectionException | InstanceNotFoundException | MBeanException | AttributeNotFoundException ex) {
      JSONObject error = new JSONObject();
      error.put("error", ex.getMessage());
      error.put("stack", getStackTrace(ex));
      res.add(error);
    }

    // WAS Perf based metrics
    try {
      MBeanServer server = AdminServiceFactory.getMBeanFactory().getMBeanServer();
      Set<ObjectName> objectsNames = new HashSet<>();
      ObjectName perfName = getPrefObjectName(server);
      for (String ibmPerfsmBean : wasPerfsmBeans) {
        queryName = new ObjectName(ibmPerfsmBean + ",*");
        objectsNames.addAll(server.queryNames(queryName, null));
        res.addAll(getObjectStats(server, perfName, objectsNames));
      }
    } catch (IOException | MalformedObjectNameException | ReflectionException | InstanceNotFoundException | MBeanException | AttributeNotFoundException ex) {
      JSONObject error = new JSONObject();
      error.put("error", ex.getMessage());
      error.put("stack", getStackTrace(ex));
      res.add(error);
    }

    try (PrintWriter out = response.getWriter()) {
      res.serialize(out);
    }
  }
}
