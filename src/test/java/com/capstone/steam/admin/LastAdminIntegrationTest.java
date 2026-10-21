package com.capstone.steam.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.capstone.steam.common.exception.ConflictException;
import com.capstone.steam.user.RoleName;
import com.capstone.steam.user.RoleRepository;
import com.capstone.steam.user.User;
import com.capstone.steam.user.UserRepository;
import com.capstone.steam.user.UserService;

/**
 * Siempre queda al menos un administrador activo, también cuando dos admins se desactivan a la vez (hilos y
 * commits reales, por eso este test usa su propia H2 y no es transaccional).
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:last_admin;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.mail.username=" // el .env local puede traer un Gmail: este test no envía correos
})
class LastAdminIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private TransactionTemplate tx;
    @Autowired private JdbcTemplate jdbc;

    private Long adminA;
    private Long adminB;

    @BeforeEach
    void twoAdmins() {
        // el admin del seed (admin) no cuenta en este test: así los dos de aquí son los únicos activos
        jdbc.update("UPDATE users SET enabled = false WHERE username = 'admin'");
        adminA = createAdmin("admin_a");
        adminB = createAdmin("admin_b");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM user_roles WHERE user_id IN (?, ?)", adminA, adminB);
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", adminA, adminB);
        jdbc.update("UPDATE users SET enabled = true WHERE username = 'admin'");
    }

    private Long createAdmin(String username) {
        return tx.execute(status -> {
            User user = new User(username, username + "@test.com", "hash-no-usado");
            user.setEmailVerified(true);
            user.addRole(roleRepository.findByName(RoleName.ROLE_USER).orElseThrow());
            user.addRole(roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow());
            return userRepository.save(user).getId();
        });
    }

    private long activeAdmins() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id
                 WHERE r.name = 'ROLE_ADMIN' AND u.enabled = true AND u.deletion_scheduled_at IS NULL AND u.deleted_at IS NULL
                   AND u.id IN (?, ?)""", Long.class, adminA, adminB);
    }

    @Test
    void removingAdminRoleFromAnotherAdminWorksWhileOneRemains() {
        userService.updateRoles(adminA, adminB, Set.of(RoleName.ROLE_USER));

        assertThat(activeAdmins()).isEqualTo(1);
    }

    @Test
    void concurrentMutualDisableLeavesOneAdmin() throws Exception {
        List<Object> results = runTogether(List.of(
                () -> userService.setEnabled(adminA, adminB, false),
                () -> userService.setEnabled(adminB, adminA, false)));

        assertThat(results.stream().filter(r -> !(r instanceof Exception)).count()).isEqualTo(1);
        List<Object> rejected = results.stream().filter(r -> r instanceof Exception).toList();
        assertThat(rejected).hasSize(1).first().isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("user.lastAdmin");
        assertThat(activeAdmins()).isEqualTo(1);
    }

    @Test
    void concurrentMutualRoleRemovalLeavesOneAdmin() throws Exception {
        List<Object> results = runTogether(List.of(
                () -> userService.updateRoles(adminA, adminB, Set.of(RoleName.ROLE_USER)),
                () -> userService.updateRoles(adminB, adminA, Set.of(RoleName.ROLE_USER))));

        assertThat(results.stream().filter(r -> !(r instanceof Exception)).count()).isEqualTo(1);
        List<Object> rejected = results.stream().filter(r -> r instanceof Exception).toList();
        assertThat(rejected).hasSize(1).first().isInstanceOf(ConflictException.class)
                .extracting(e -> ((ConflictException) e).getCode()).isEqualTo("user.lastAdmin");
        assertThat(activeAdmins()).isEqualTo(1);
    }

    private List<Object> runTogether(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> task : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return task.call();
                } catch (RuntimeException e) {
                    return e;
                }
            }));
        }
        ready.await();
        go.countDown();
        List<Object> results = new ArrayList<>();
        for (Future<Object> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }
}
