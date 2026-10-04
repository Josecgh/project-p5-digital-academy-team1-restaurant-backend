package dev.team1.security;

import dev.team1.roles.RoleEntity;
import dev.team1.roles.RoleRepository;
import dev.team1.users.UserEntity;
import dev.team1.users.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;

    @Value("/${api-endpoint}")
    private String apiEndpoint;

    private UserEntity adminUser;
    private UserEntity regularUser;

    @BeforeEach
    void setUp() {
        RoleEntity adminRole = roleRepository.findByName("ROLE_ADMIN")
            .orElseGet(() -> roleRepository.save(newRole("ROLE_ADMIN")));
        RoleEntity userRole = roleRepository.findByName("ROLE_CUSTOMER")
            .orElseGet(() -> roleRepository.save(newRole("ROLE_CUSTOMER")));

        adminUser = userRepository.save(newUser("admin@test.com", Set.of(adminRole)));
        regularUser = userRepository.save(newUser("user@test.com", Set.of(userRole)));
    }

    @Test
    void administration_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(apiEndpoint + "/products/administration"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void administration_withUserRole_returns403() throws Exception {
        String token = jwtService.generateAuthToken(regularUser.getEmail(), "ROLE_CUSTOMER").token();

        mockMvc.perform(get(apiEndpoint + "/products/administration")
                .cookie(new Cookie("access_token", token)))
            .andExpect(status().isForbidden());
    }

    @Test
    void administration_withAdminRole_returns200() throws Exception {
        String token = jwtService.generateAuthToken(adminUser.getEmail(), "ROLE_ADMIN").token();

        mockMvc.perform(get(apiEndpoint + "/products/administration")
                .cookie(new Cookie("access_token", token)))
            .andExpect(status().isOk());
    }

    @Test
    void cloudAutomationStatus_withUserRole_returns403() throws Exception {
        String token = jwtService.generateAuthToken(regularUser.getEmail(), "ROLE_CUSTOMER").token();

        mockMvc.perform(get(apiEndpoint + "/cloud-automation/status")
                .cookie(new Cookie("access_token", token)))
            .andExpect(status().isForbidden());
    }

    @Test
    void cloudAutomationStatus_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(apiEndpoint + "/cloud-automation/status"))
            .andExpect(status().isUnauthorized());
    }

    private RoleEntity newRole(String name) {
        RoleEntity role = new RoleEntity();
        role.setName(name);
        return role;
    }

    private UserEntity newUser(String email, Set<RoleEntity> roles) {
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPassword("irrelevant");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setAddress("Test address");
        user.setPostalCode("00000");
        user.setCity("Test city");
        user.setRoles(roles);
        return user;
    }
}
