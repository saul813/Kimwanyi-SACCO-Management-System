package com.example.bean;

import com.example.dao.AccountDAO;
import com.example.dao.UserDAO;
import com.example.model.SavingsAccount;
import com.example.model.User;
import jakarta.annotation.PostConstruct;
import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Named("adminMemberBean")
@ViewScoped
public class AdminMemberBean implements Serializable {
    private static final long serialVersionUID = 1L;

    @Inject private UserDAO userDAO;
    @Inject private AccountDAO accountDAO;

    private List<User> allMembers;

    @PostConstruct
    public void init() {
        reloadRegistry();
    }

    public void reloadRegistry() {
        this.allMembers = userDAO.findAllUsers();
    }

    public void updateStatus(Long userId, String targetStatus) {
        FacesContext context = FacesContext.getCurrentInstance();
        try {
            User targetUser = userDAO.findById(userId);
            if (targetUser != null) {
                targetUser.setStatus(targetStatus);
                userDAO.saveUser(targetUser);

                if ("ACTIVE".equals(targetStatus)) {
                    SavingsAccount existingAccount = accountDAO.findByUserId(userId);
                    if (existingAccount == null) {
                        SavingsAccount newAccount = new SavingsAccount();
                        newAccount.setMember(targetUser);
                        newAccount.setAccountNumber("ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                        newAccount.setBalance(BigDecimal.ZERO);
                        accountDAO.saveAccount(newAccount);
                    }
                }

                context.addMessage(null, new FacesMessage(
                        FacesMessage.SEVERITY_INFO, "Status Updated", "Profile modified to " + targetStatus
                ));
                reloadRegistry();
            }
        } catch (Exception e) {
            e.printStackTrace();
            context.addMessage(null, new FacesMessage(
                    FacesMessage.SEVERITY_ERROR, "Operation Aborted", "Database communication breakdown."
            ));
        }
    }

    public List<User> getAllMembers() { return allMembers; }
}
