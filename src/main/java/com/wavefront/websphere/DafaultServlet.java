/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wavefront.websphere;

import com.ibm.json.java.JSONObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public abstract class DafaultServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected final void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        safeProcessRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected final void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        safeProcessRequest(request, response);
    }

    protected void safeProcessRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            processRequest(request, response);
        } catch (Throwable ex) {
            JSONObject error = new JSONObject();
            error.put("error", ex.toString());
            error.put("stack", getStackTrace(ex));
            try (PrintWriter out = response.getWriter()) {
                error.serialize(out);
            }
        }
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code>
     * methods.
     *
     * @param request servlet request
     * @param response servlet response
     * @throws ServletException if a servlet-specific error occurs
     * @throws IOException if an I/O error occurs
     */
    protected abstract void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;

    protected static String getStackTrace(Throwable aThrowable) {
        Writer result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        aThrowable.printStackTrace(printWriter);
        return result.toString();
    }

    protected static JSONObject toMap(CompositeData data) {
        JSONObject res = new JSONObject();
        Set<String> keySet = data.getCompositeType().keySet();
        if (keySet != null) {
            keySet.stream().filter((key) -> (key != null)).forEachOrdered((key) -> {
                Object value = data.get(key);
                if (value != null) {
                    res.put(key, value);
                }
            });
        }
        return res;
    }

    protected static Map<String, String> getTags(ObjectName objectName) {
        Map<String, String> tags = new HashMap<>();
        tags.put("name", objectName.getKeyProperty("name"));
        tags.put("process", objectName.getKeyProperty("process"));
        tags.put("node", objectName.getKeyProperty("node"));
        tags.put("cell", objectName.getKeyProperty("cell"));
        tags.put("mbeanIdentifier", objectName.getKeyProperty("mbeanIdentifier"));
        tags.put("fullname", objectName.getCanonicalName());

        tags = tags.entrySet().stream().filter(p -> p.getValue() != null).collect(Collectors.toMap(p -> p.getKey(), p -> p.getValue()));
        return tags;
    }

}
