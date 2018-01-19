/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wavefront.websphere;

import com.ibm.json.java.JSONObject;
import com.ibm.websphere.management.AdminServiceFactory;

import java.io.IOException;
import java.io.PrintWriter;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static com.wavefront.websphere.DafaultServlet.getPrefObjectName;
import static com.wavefront.websphere.DafaultServlet.getStackTrace;

public class InfoServlet extends HttpServlet {

  private static final long serialVersionUID = 1L;

  /**
   * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
   *
   * @param request  servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException      if an I/O error occurs
   */
  protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String wasVersion = "";
    JSONObject info = new JSONObject();

    MBeanServer server = AdminServiceFactory.getMBeanFactory().getMBeanServer();
    try {
      ObjectName perfName = getPrefObjectName(server);
      wasVersion = perfName.getKeyProperty("version");
    } catch (MalformedObjectNameException ex) {
      info.put("error", ex.getMessage());
      info.put("stack", getStackTrace(ex));
    }

    java.util.Properties prop = new java.util.Properties();
    prop.load(getServletContext().getResourceAsStream("/META-INF/MANIFEST.MF"));
    String warVersion = prop.getProperty("version");

    info.put("wasmonitor.version", warVersion);
    info.put("websphere.version", wasVersion);

    try (PrintWriter out = response.getWriter()) {
      info.serialize(out);
    }

  }

  /**
   * Handles the HTTP <code>GET</code> method.
   *
   * @param request  servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException      if an I/O error occurs
   */
  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Handles the HTTP <code>POST</code> method.
   *
   * @param request  servlet request
   * @param response servlet response
   * @throws ServletException if a servlet-specific error occurs
   * @throws IOException      if an I/O error occurs
   */
  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    processRequest(request, response);
  }

  /**
   * Returns a short description of the servlet.
   *
   * @return a String containing servlet description
   */
  @Override
  public String getServletInfo() {
    return "Short description";
  }

}
