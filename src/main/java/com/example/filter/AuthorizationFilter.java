package com.example.filter;

import com.example.bean.AuthBean;
import com.example.model.User;
import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(urlPatterns = {"/admin/*", "/member/*"})
public class AuthorizationFilter implements Filter {

    @Inject private AuthBean authBean;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String contextPath = httpRequest.getContextPath();
        String uri = httpRequest.getRequestURI();

        User loggedInUser = authBean != null ? authBean.getLoggedInUser() : null;
        if (loggedInUser == null) {
            httpResponse.sendRedirect(contextPath + "/login.xhtml");
            return;
        }

        if (uri.contains("/admin/") && loggedInUser.getRole() == User.Role.MEMBER) {
            httpResponse.sendRedirect(contextPath + "/member/member-dashboard.xhtml");
            return;
        }

        if (uri.contains("/member/") && loggedInUser.getRole() != User.Role.MEMBER) {
            httpResponse.sendRedirect(contextPath + "/admin/admin-dashboard.xhtml");
            return;
        }

        chain.doFilter(request, response);
    }
}
