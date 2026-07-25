package com.example.bean;

import com.example.dao.UserDAO;
import com.example.model.User;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.SessionScoped;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.context.Flash;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;

@Named("authBean")
@SessionScoped
public class AuthBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject
    private UserDAO userDAO;

    private User newUser;
    private User loggedInUser;

    // Login input parameters
    private String loginEmail;
    private String loginPassword;

    @PostConstruct
    public void init() {
        resetRegistrationForm();
    }

    public void resetRegistrationForm() {
        newUser = new User();
        newUser.setRole(User.Role.MEMBER);
        newUser.setStatus("PENDING");
    }

    /**
     * Processes member self-registration submissions.
     */
    public String register() {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            String formattedNIN = newUser.getNationalId().trim().toUpperCase();
            newUser.setNationalId(formattedNIN);
            String formattedEmail = newUser.getEmail().trim().toLowerCase();
            newUser.setEmail(formattedEmail);

            User existingProfile = userDAO.findByNationalId(formattedNIN);
            if (existingProfile != null) {
                context.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_ERROR, "Registration Failed",
                        "A member with this National ID (NIN) is already registered."
                ));
                return null;
            }

            User existingEmailProfile = userDAO.findByEmail(formattedEmail);
            if (existingEmailProfile != null) {
                context.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_ERROR, "Registration Failed",
                        "A member with this email address is already registered."
                ));
                return null;
            }

            userDAO.saveUser(newUser);
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_INFO, "Submission Successful",
                    "Account created and submitted for review. Please login after admin approval."
            ));
            Flash flash = context.getExternalContext().getFlash();
            flash.setKeepMessages(true);

            resetRegistrationForm();
            return "/login?faces-redirect=true";
        } catch (Exception e) {
            e.printStackTrace();
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_FATAL, "System Error", "An unexpected error occurred."
            ));
            return null;
        }
    }

    /**
     * Processes portal login requests and routes users based on system roles.
     */
    public String login() {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            String formattedEmail = loginEmail.trim().toLowerCase();
            User user = userDAO.findByEmail(formattedEmail);

            // Audit Check 1: User existence and password validation
            if (user == null || !user.getPassword().equals(loginPassword)) {
                context.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_ERROR, "Authentication Failed", "Invalid Email or Password."
                ));
                return null;
            }

            // Audit Check 2: Account activation guard
            if ("PENDING".equals(user.getStatus())) {
                context.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_WARN, "Pending Approval",
                        "Your registration was received. Please wait for admin approval before accessing the member dashboard."
                ));
                return null;
            } else if ("DEACTIVATED".equals(user.getStatus())) {
                context.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_ERROR, "Access Terminated", "This profile has been deactivated. Contact management."
                ));
                return null;
            }

            // Bind profile attributes to current HTTP Session container context
            this.loggedInUser = user;

            // Role-Based Access Control Routing Logic
            if (user.getRole() == User.Role.MEMBER) {
                return "/member/member-dashboard?faces-redirect=true";
            } else {
                // ADMIN, MANAGER, and CASHIER roles go to the internal management workspace
                return "/admin/admin-dashboard?faces-redirect=true";
            }
        } catch (Exception e) {
            e.printStackTrace();
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_FATAL, "System Error", "Database communication failure during login."
            ));
            return null;
        }
    }

    /**
     * Clears session tracking variables and performs clean logout serialization routines.
     */
    public String logout() {
        FacesContext.getCurrentInstance().getExternalContext().invalidateSession();
        this.loggedInUser = null;
        this.loginEmail = null;
        this.loginPassword = null;
        return "/login?faces-redirect=true";
    }

    // Getters and Setters
    public User getNewUser() { return newUser; }
    public void setNewUser(User newUser) { this.newUser = newUser; }

    public User getLoggedInUser() { return loggedInUser; }
    public void setLoggedInUser(User loggedInUser) { this.loggedInUser = loggedInUser; }

    public String getLoginEmail() { return loginEmail; }
    public void setLoginEmail(String loginEmail) { this.loginEmail = loginEmail; }

    public String getLoginPassword() { return loginPassword; }
    public void setLoginPassword(String loginPassword) { this.loginPassword = loginPassword; }
}
