/*
 * Copyright (c) 2015 Fraunhofer FOKUS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.project.openbaton.nubomedia.paas.security.authentication;

import org.project.openbaton.nubomedia.paas.repository.security.UserRepository;
import org.project.openbaton.nubomedia.paas.model.persistence.security.Project;
import org.project.openbaton.nubomedia.paas.model.persistence.security.Role;
import org.project.openbaton.nubomedia.paas.model.persistence.security.User;
import org.project.openbaton.nubomedia.paas.security.interfaces.ProjectManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

@Component
public class CustomUserDetailsService
    implements UserDetailsService, CommandLineRunner, UserDetailsManager {

  @Autowired private UserRepository userRepository;

  @Autowired
  @Qualifier("inMemManager")
  private UserDetailsManager inMemManager;

  private Logger log = LoggerFactory.getLogger(this.getClass());

  @Value("${paas.security.admin.password:nub0m3d14}")
  private String adminPwd;
  //@Value("${paas.security.guest.password:guest}")
  //private String guestPwd;
  @Autowired private ProjectManagement projectManagement;

  @Value("${paas.security.project.name:default}")
  private String projectDefaultName;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    return inMemManager.loadUserByUsername(username);
  }

  @Override
  public void run(String... args) throws Exception {

    log.debug("Creating initial Users...");

    if (userRepository.findFirstByUsername("admin") == null) {
      User ob_admin = new User();
      ob_admin.setUsername("admin");
      ob_admin.setEnabled(true);
      ob_admin.setPassword(BCrypt.hashpw(adminPwd, BCrypt.gensalt(12)));
      Set<Role> roles = new HashSet<>();
      Role role = new Role();
      role.setRole(Role.RoleEnum.NUBOMEDIA_ADMIN);
      role.setProject("*");
      roles.add(role);
      ob_admin.setRoles(roles);
      userRepository.save(ob_admin);
    }
    if (!inMemManager.userExists("admin")) {
      UserDetails admin =
          new org.springframework.security.core.userdetails.User(
              "admin",
              BCrypt.hashpw(adminPwd, BCrypt.gensalt(12)),
              true,
              true,
              true,
              true,
              AuthorityUtils.createAuthorityList("NUBOMEDIA_ADMIN:*"));
      inMemManager.createUser(admin);
    } else {
      log.debug("Admin" + inMemManager.loadUserByUsername("admin"));
    }

    /*if (userRepository.findFirstByUsername("guest") == null) {
        User ob_guest = new User();
        ob_guest.setUsername("guest");
        ob_guest.setPassword(BCrypt.hashpw(guestPwd, BCrypt.gensalt(12)));
        ob_guest.setEnabled(true);
        Set<Role> roles = new HashSet<>();
        Role role = new Role();
        role.setRole(Role.RoleEnum.GUEST);
        role.setProject("*");
        roles.add(role);
        ob_guest.setRoles(roles);
        userRepository.save(ob_guest);
    }
    if (!inMemManager.userExists("guest")) {
        UserDetails guest = new org.springframework.security.core.userdetails.User("guest", BCrypt.hashpw(guestPwd, BCrypt.gensalt(12)), true, true, true, true, AuthorityUtils.createAuthorityList("GUEST:*"));
        inMemManager.createUser(guest);
    }*/

    log.debug("User in the DB: ");
    for (User user : userRepository.findAll()) {
      log.debug("" + user);
    }

    for (User user : userRepository.findAll()) {
      if (!user.getUsername().equals("admin") && !user.getUsername().equals("guest")) {
        String[] roles = new String[user.getRoles().size()];
        for (int i = 0; i < user.getRoles().size(); i++) {
          roles[i] =
              user.getRoles().toArray(new Role[0])[i].getRole()
                  + ":"
                  + user.getRoles().toArray(new Role[0])[i].getProject();
        }
        UserDetails userDetails =
            new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true,
                true,
                true,
                AuthorityUtils.createAuthorityList(roles));
        inMemManager.createUser(userDetails);
      }
    }

    log.debug("Users in UserDetailManager: ");
    log.info("ADMIN: " + inMemManager.loadUserByUsername("admin"));
    //log.debug("GUEST: " + inMemManager.loadUserByUsername("guest"));

    log.debug("Creating initial Project...");

    if (projectManagement.queryByName(projectDefaultName) == null) {
      Project project = new Project();
      project.setName(projectDefaultName);

      projectManagement.add(project);
      log.debug("Created project: " + project);
    } else log.debug("Project " + projectDefaultName + " already existing");
  }

  @Override
  public void createUser(UserDetails user) {
    this.inMemManager.createUser(user);
  }

  @Override
  public void updateUser(UserDetails user) {
    inMemManager.updateUser(user);
    User userToUpdate = userRepository.findFirstByUsername(user.getUsername());
    userToUpdate.setPassword(user.getPassword());
    for (GrantedAuthority authority : user.getAuthorities()) {
      StringTokenizer stringTokenizer = new StringTokenizer(authority.getAuthority(), ":");
      String rl = stringTokenizer.nextToken();
      String pj = stringTokenizer.nextToken();
      boolean found = false;
      for (Role role : userToUpdate.getRoles()) {
        if (role.getProject().equals(pj)) {
          role.setRole(Role.RoleEnum.valueOf(rl));
          found = true;
        }
      }
      if (!found) {
        Role role = new Role();
        role.setRole(Role.RoleEnum.valueOf(rl));
        role.setProject(pj);
        userToUpdate.getRoles().add(role);
      }
    }
    userRepository.save(userToUpdate);
  }

  @Override
  public void deleteUser(String username) {
    inMemManager.deleteUser(username);
    userRepository.delete(userRepository.findFirstByUsername(username).getId());
  }

  @Override
  public void changePassword(String oldPassword, String newPassword) {
    inMemManager.changePassword(oldPassword, newPassword);
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof AnonymousAuthenticationToken)) {
      String currentUserName = authentication.getName();
      User user = userRepository.findFirstByUsername(currentUserName);
      user.setPassword(newPassword);
      userRepository.save(user);
      return;
    }
  }

  @Override
  public boolean userExists(String username) {
    return inMemManager.userExists(username)
        && (userRepository.findFirstByUsername(username) != null);
  }
}
