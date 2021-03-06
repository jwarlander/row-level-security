package com.exasol.rls.administration.scripts;

import static com.exasol.adapter.dialects.rls.RowLevelSecurityDialectConstants.EXA_RLS_USERS_TABLE_NAME;
import static com.exasol.adapter.dialects.rls.RowLevelSecurityDialectConstants.EXA_ROLES_MAPPING_TABLE_NAME;
import static com.exasol.matcher.ResultSetStructureMatcher.table;
import static com.exasol.tools.TestsConstants.PATH_TO_DELETE_RLS_ROLE;
import static com.exasol.tools.TestsConstants.PATH_TO_EXA_RLS_BASE;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Stream;

import com.exasol.dbbuilder.dialects.Table;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.testcontainers.containers.JdbcDatabaseContainer.NoDriverFoundException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.exasol.containers.ExasolContainer;
import com.exasol.containers.ExasolContainerConstants;
import com.exasol.matcher.ResultSetStructureMatcher;
import com.exasol.matcher.ResultSetStructureMatcher.Builder;

// [itest->dsn~delete-a-role~1]
@Tag("integration")
@Testcontainers
class DeleteRlsRoleIT extends AbstractAdminScriptIT {
    @Container
    private static final ExasolContainer<? extends ExasolContainer<?>> container = new ExasolContainer<>(
            ExasolContainerConstants.EXASOL_DOCKER_IMAGE_REFERENCE);
    private static final String EXA_RLS_USERS = "EXA_RLS_USERS";
    private static Table rolesTable;
    private static Table usersTable;

    @BeforeAll
    static void beforeAll() throws SQLException, IOException {
        initialize(container, "DELETE_RLS_ROLE", PATH_TO_EXA_RLS_BASE, PATH_TO_DELETE_RLS_ROLE);
        rolesTable = schema.createTable(EXA_ROLES_MAPPING_TABLE_NAME, "ROLE_NAME", "VARCHAR(128)", "ROLE_ID",
                "DECIMAL(2,0)");
        usersTable = schema.createTable(EXA_RLS_USERS, "EXA_USER_NAME", "VARCHAR(128)", "EXA_ROLE_MASK",
                "DECIMAL(20,0)");
    }

    // [itest->dsn~delete-rls-role-removes-a-role-from-administrative-tables~1]
    @Override
    protected Connection getConnection() throws NoDriverFoundException, SQLException {
        return container.createConnection("");
    }

    @AfterEach
    void afterEach() throws SQLException {
        execute("TRUNCATE TABLE " + getRolesMappingTableName());
        execute("TRUNCATE TABLE " + getUsersTableName());
    }

    private String getRolesMappingTableName() {
        return schema.getFullyQualifiedName() + "." + EXA_ROLES_MAPPING_TABLE_NAME;
    }

    private String getUsersTableName() {
        return schema.getFullyQualifiedName() + "." + EXA_RLS_USERS_TABLE_NAME;
    }

    // [itest->dsn~delete-rls-role-removes-a-role-from-administrative-tables~1]
    @CsvSource({ "'Sales', 'Development', 'Finance', 'Support'", "'Development', 'Finance', 'Sales', 'Support'",
            "'Finance', 'Development', 'Sales', 'Support'", "'Support',  'Development', 'Finance', 'Sales'" })
    @ParameterizedTest
    void testDeleteRlsRoleFromExaRolesMapping(final String roleToDelete, final String remainingRole1,
            final String remainingRole2, final String remainingRole3) throws SQLException {
        createRolesMapping();
        script.execute(roleToDelete);
        assertThat(query("SELECT ROLE_NAME FROM " + getRolesMappingTableName() + " ORDER BY ROLE_NAME"),
                table().row(remainingRole1).row(remainingRole2).row(remainingRole3).matchesFuzzily());
    }

    private void createRolesMapping() {
        rolesTable.insert("Sales", 1) //
                .insert("Development", 2) //
                .insert("Finance", 3) //
                .insert("Support", 4);
    }

    // [itest->dsn~delete-rls-role-removes-a-role-from-administrative-tables~1]
    @Test
    void testDeleteRlsRoleUnknownRole() throws SQLException {
        rolesTable.insert("Sales", 1) //
                .insert("Development", 2);
        assertScriptThrows("Unable to delete RLS role \"Support\" because it does not exist.", "Support");
    }

    // [itest->dsn~delete-rls-role-removes-a-role-from-user-table~1]
    @ParameterizedTest
    @MethodSource("provideValuesForTestDeleteRlsRoleFromExaRlsUsers")
    void testDeleteRlsRoleFromExaRlsUsers(final String roleToDelete, final Object[][] expectedRows)
            throws SQLException {
        createRolesMapping();
        usersTable.insert("RLS_USR_1", 15) //
                .insert("RLS_USR_2", 9);
        script.execute(roleToDelete);
        final Builder tableMatcherBuilder = createResultSetMatcher(expectedRows);
        assertThat(query("SELECT EXA_USER_NAME, EXA_ROLE_MASK FROM " + schema.getName() + "." + EXA_RLS_USERS
                + " ORDER BY EXA_USER_NAME"), tableMatcherBuilder.matchesFuzzily());
    }

    private ResultSetStructureMatcher.Builder createResultSetMatcher(final Object[][] expectedRows) {
        final Builder tableMatcherBuilder = table();
        for (final Object[] expecteduserRow : expectedRows) {
            tableMatcherBuilder.row(expecteduserRow);
        }
        return tableMatcherBuilder;
    }

    private static Stream<Arguments> provideValuesForTestDeleteRlsRoleFromExaRlsUsers() {
        return Stream.of(Arguments.of("Sales", new Object[][] { { "RLS_USR_1", 14 }, { "RLS_USR_2", 8 } }), //
                Arguments.of("Development", new Object[][] { { "RLS_USR_1", 13 }, { "RLS_USR_2", 9 } }), //
                Arguments.of("Finance", new Object[][] { { "RLS_USR_1", 11 }, { "RLS_USR_2", 9 } }), //
                Arguments.of("Support", new Object[][] { { "RLS_USR_1", 7 }, { "RLS_USR_2", 1 } }));
    }

    // [itest->dsn~delete-rls-role-removes-a-role-from-roles-secured-tables~1]
    @ParameterizedTest
    @MethodSource("provideValuesForTestDeleteRlsRoleFromPayloadTable")
    void testDeleteRlsRoleFromPayloadTable(final String roleToDelete, final Object[][] expectedRows)
            throws SQLException {
        final String protectedTableName = schema.getFullyQualifiedName() + ".T";
        schema.createTable("T", "C1", "VARCHAR(4)", "EXA_ROW_ROLES", "DECIMAL(20,0)") //
                .insert("Row1", 1) //
                .insert("Row2", 6) //
                .insert("Row3", 9) //
                .insert("Row4", 15);
        try {
            createRolesMapping();
            final Builder matcherBuilder = createResultSetMatcher(expectedRows);
            script.execute(roleToDelete);
            assertThat(query("SELECT C1, EXA_ROW_ROLES FROM " + protectedTableName + " ORDER BY C1"),
                    matcherBuilder.matchesFuzzily());
        } finally {
            execute("DROP TABLE " + protectedTableName);
        }
    }

    private static Stream<Arguments> provideValuesForTestDeleteRlsRoleFromPayloadTable() {
        return Stream.of(
                Arguments.of("Sales", new Object[][] { { "Row1", 0 }, { "Row2", 6 }, { "Row3", 8 }, { "Row4", 14 } }), //
                Arguments.of("Development",
                        new Object[][] { { "Row1", 1 }, { "Row2", 4 }, { "Row3", 9 }, { "Row4", 13 } }), //
                Arguments.of("Finance", new Object[][] { { "Row1", 1 }, { "Row2", 2 }, { "Row3", 9 }, { "Row4", 11 } }), //
                Arguments.of("Support", new Object[][] { { "Row1", 1 }, { "Row2", 6 }, { "Row3", 1 }, { "Row4", 7 } }));
    }
}