package com.test.app.web.rest;

import com.codahale.metrics.annotation.Timed;
import com.test.app.domain.Authority;
import com.test.app.domain.PersistentToken;
import com.test.app.domain.User;
import com.test.app.repository.AuthorityRepository;
import com.test.app.repository.PersistentTokenRepository;
import com.test.app.repository.MedUserRepository;
import com.test.app.repository.UserRepository;
import com.test.app.security.SecurityUtils;
import com.test.app.service.MailService;
import com.test.app.service.UserService;
import com.test.app.domain.*;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import scala.collection.mutable.HashSet;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

/**
 * REST controller for managing the current user's account.
 */
@RestController
@RequestMapping("/api")
public class AccountResource {

    private final Logger log = LoggerFactory.getLogger(AccountResource.class);

    @Inject
    private UserRepository userRepository;

    @Inject
    private UserService userService;

    @Inject
    private PersistentTokenRepository persistentTokenRepository;

    @Inject
    private MailService mailService;

    @Inject
    MedUserRepository repo;
    
    @Inject
    private AuthorityRepository authorityRepository;

    @Inject
    MedUserRepository medUserRepository;
    /**
     * POST  /register -> register the user.
     */
    @RequestMapping(value = "/register",
            method = RequestMethod.POST,
            produces = MediaType.TEXT_PLAIN_VALUE)
    @Timed
    public ResponseEntity<?> registerAccount(@Valid @RequestBody User dto, HttpServletRequest request) {
        User user = userRepository.findOneByLogin(dto.getLogin());
        if (user != null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("login already in use");
        } else {
            if (userRepository.findOneByEmail(dto.getEmail()) != null) {
                return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("e-mail address already in use");
            }
            Set<Authority> authorities = new java.util.HashSet<Authority>();
            if (dto.isDoctor()) {
            	authorities.add(authorityRepository.findOne("ROLE_DOCTOR"));
            }
            if (dto.isHospitalAdmin()) {
            	authorities.add(authorityRepository.findOne("ROLE_HOSPITAL_ADMIN"));
            }
            authorities.add(authorityRepository.findOne("ROLE_USER"));
            user = userService.createUserInformation(dto.getLogin(), dto.getPassword(),
            dto.getName(), dto.getName(), dto.getEmail().toLowerCase(),
            null, authorities, dto.isDoctor(), dto.isHospitalAdmin());
            
            System.out.println("successfully saved ");
            String baseUrl = request.getScheme() + // "http"
            "://" +                            // "://"
            request.getServerName() +          // "myhost"
            ":" +                              // ":"
            request.getServerPort();           // "80"

            mailService.sendActivationEmail(user, baseUrl);
            return new ResponseEntity<>(HttpStatus.CREATED);
        }
    }

    /**
     * GET  /activate -> activate the registered user.
     */
    @RequestMapping(value = "/activate",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<String> activateAccount(@RequestParam(value = "key") String key) {
        User user = userService.activateRegistration(key);
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    /**
     * GET  /authenticate -> check if the user is authenticated, and return its login.
     */
    @RequestMapping(value = "/authenticate",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public String isAuthenticated(HttpServletRequest request) {
        log.debug("REST request to check if the current user is authenticated");
        return request.getRemoteUser();
    }

    /**
     * GET  /account -> get the current user.
     */
    @RequestMapping(value = "/account",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<User> getAccount() {
        User user = userService.getUserWithAuthorities();
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        List<String> roles = new ArrayList<>();
        for (Authority authority : user.getAuthorities()) {
            roles.add(authority.getName());
        }
        user.setRoles(roles);
        return new ResponseEntity<>(
            user,
            HttpStatus.OK);
    }

    /**
     * POST  /account -> update the current user information.
     */
    @RequestMapping(value = "/account",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<String> saveAccount(@RequestBody User medUserDto) {
        User userHavingThisLogin = userRepository.findOneByLogin(medUserDto.getLogin());
        if (userHavingThisLogin != null && !userHavingThisLogin.getLogin().equals(SecurityUtils.getCurrentLogin())) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        userRepository.save(medUserDto);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * POST  /change_password -> changes the current user's password
     */
    @RequestMapping(value = "/account/change_password",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<?> changePassword(@RequestBody String password) {
        if (!checkPasswordLength(password)) {
            return new ResponseEntity<>("Incorrect password", HttpStatus.BAD_REQUEST);
        }
        userService.changePassword(password);
        return new ResponseEntity<>(HttpStatus.OK);
    }

    /**
     * GET  /account/sessions -> get the current open sessions.
     */
    @RequestMapping(value = "/account/sessions",
            method = RequestMethod.GET,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<List<PersistentToken>> getCurrentSessions() {
        User user = userRepository.findOneByLogin(SecurityUtils.getCurrentLogin());
        if (user == null) {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<>(
            persistentTokenRepository.findByUser(user),
            HttpStatus.OK);
    }

    /**
     * DELETE  /account/sessions?series={series} -> invalidate an existing session.
     *
     * - You can only delete your own sessions, not any other user's session
     * - If you delete one of your existing sessions, and that you are currently logged in on that session, you will
     *   still be able to use that session, until you quit your browser: it does not work in real time (there is
     *   no API for that), it only removes the "remember me" cookie
     * - This is also true if you invalidate your current session: you will still be able to use it until you close
     *   your browser or that the session times out. But automatic login (the "remember me" cookie) will not work
     *   anymore.
     *   There is an API to invalidate the current session, but there is no API to check which session uses which
     *   cookie.
     */
    @RequestMapping(value = "/account/sessions/{series}",
            method = RequestMethod.DELETE)
    @Timed
    public void invalidateSession(@PathVariable String series) throws UnsupportedEncodingException {
        String decodedSeries = URLDecoder.decode(series, "UTF-8");
        User user = userRepository.findOneByLogin(SecurityUtils.getCurrentLogin());
        List<PersistentToken> persistentTokens = persistentTokenRepository.findByUser(user);
        for (PersistentToken persistentToken : persistentTokens) {
            if (StringUtils.equals(persistentToken.getSeries(), decodedSeries)) {
                persistentTokenRepository.delete(decodedSeries);
            }
        }
    }

    @RequestMapping(value = "/account/reset_password/init",
        method = RequestMethod.POST,
        produces = MediaType.TEXT_PLAIN_VALUE)
    @Timed
    public ResponseEntity<?> requestPasswordReset(@RequestBody String mail, HttpServletRequest request) {
        
        User user = userService.requestPasswordReset(mail);

        if (user != null) {
          String baseUrl = request.getScheme() +
              "://" +
              request.getServerName() +
              ":" +
              request.getServerPort();
          mailService.sendPasswordResetMail(user, baseUrl);
          return new ResponseEntity<>("e-mail was sent", HttpStatus.OK);
        } else {
          return new ResponseEntity<>("e-mail address not registered", HttpStatus.BAD_REQUEST);
        }
        
    }

    @RequestMapping(value = "/account/reset_password/finish",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<String> finishPasswordReset(@RequestParam(value = "key") String key, @RequestParam(value = "newPassword") String newPassword) {
        if (!checkPasswordLength(newPassword)) {
            return new ResponseEntity<>("Incorrect password", HttpStatus.BAD_REQUEST);
        }
        User user = userService.completePasswordReset(newPassword, key);
        if (user != null) {
          return new ResponseEntity<String>(HttpStatus.OK);
        } else {
          return new ResponseEntity<String>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean checkPasswordLength(String password) {
      return (!StringUtils.isEmpty(password) );
    }
}