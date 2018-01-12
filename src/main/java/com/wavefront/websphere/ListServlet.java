/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wavefront.websphere;

import com.ibm.json.java.JSONArray;
import com.ibm.json.java.JSONObject;
import com.ibm.websphere.management.AdminServiceFactory;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanServer;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ListServlet extends DafaultServlet {

    private static final long serialVersionUID = 1L;

    @Override
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        JSONArray res = new JSONArray();

        Set<MBeanServer> servers = new HashSet<>();
        servers.add(ManagementFactory.getPlatformMBeanServer());
        servers.add(AdminServiceFactory.getMBeanFactory().getMBeanServer());

        for (MBeanServer server : servers) {
            try {
                ObjectName queryName = new ObjectName("*:*");
                Set<ObjectName> objectsNames = new HashSet<>();
                objectsNames.addAll(server.queryNames(queryName, null));
                res.addAll(dumpObject(server, objectsNames));
            } catch (MalformedObjectNameException | ReflectionException | IntrospectionException ex) {
                JSONObject error = new JSONObject();
                error.put("error", ex.getMessage());
                error.put("stack", getStackTrace(ex));
                res.add(error);
            }
        }

        try (PrintWriter out = response.getWriter()) {
            res.serialize(out);
        }
    }

    /**
     * Returns a short description of the servlet.
     *
     * @return a String containing servlet description
     */
    @Override
    public String getServletInfo() {
        return "List all mBeans";
    }

    private static JSONArray dumpObject(MBeanServerConnection server, Set<ObjectName> objectsNames) throws IOException, ReflectionException, IntrospectionException {
        JSONArray res = new JSONArray();
        for (ObjectName objectName : objectsNames) {
            JSONObject object = new JSONObject();
            object.putAll(getTags(objectName));
            try {
                object.put("Attributes", Arrays.toString(server.getMBeanInfo(objectName).getAttributes()));
            } catch (InstanceNotFoundException ex) {
                object.put("Attributes", ex.toString());
            }
            try {
                object.put("Operations", Arrays.toString(server.getMBeanInfo(objectName).getOperations()));
            } catch (InstanceNotFoundException ex) {
                object.put("Operations", ex.toString());
            }
            res.add(object);
        }
        return res;
    }

}
