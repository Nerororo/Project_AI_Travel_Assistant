package com.example.travel.user.repository;

import com.example.travel.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer mysql = new MySQLContainer(DockerImageName.parse("mysql:8.4"));

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void savesAndFindsUserByEmail() {
		User saved = userRepository.saveAndFlush(new User("account-one@example.test", "encoded-value-one"));

		assertThat(saved.id()).isPositive();
		assertThat(saved.createdAt()).isNotNull();
		assertThat(saved.updatedAt()).isNotNull();
		assertThat(userRepository.findByEmail("account-one@example.test"))
				.get()
				.extracting(User::email, User::passwordHash)
				.containsExactly("account-one@example.test", "encoded-value-one");
	}

	@Test
	void rejectsDuplicateEmailIgnoringCase() {
		userRepository.saveAndFlush(new User("unique-account@example.test", "encoded-value-one"));

		assertThatThrownBy(() -> userRepository.saveAndFlush(
				new User("UNIQUE-ACCOUNT@example.test", "encoded-value-two")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void schemaStoresOnlyPasswordHashColumn() {
		userRepository.saveAndFlush(new User("column-check@example.test", "encoded-value-only"));

		List<String> passwordColumns = jdbcTemplate.queryForList("""
				SELECT column_name
				FROM information_schema.columns
				WHERE table_schema = DATABASE()
				  AND table_name = 'users'
				  AND column_name LIKE 'password%'
				ORDER BY column_name
				""", String.class);
		String storedHash = jdbcTemplate.queryForObject(
				"SELECT password_hash FROM users WHERE email = ?",
				String.class,
				"column-check@example.test");

		assertThat(passwordColumns).containsExactly("password_hash");
		assertThat(storedHash).isEqualTo("encoded-value-only");
	}
}
