package ru.meridor.tools.plugin.impl;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import ru.meridor.tools.plugin.Dependency;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class DependencyContainerTest {

    private static final String DEPENDENCY_NAME = "some-name";
    private static final Dependency DEPENDENCY_WITHOUT_VERSION = new DependencyContainer(DEPENDENCY_NAME);
    private static final String DEPENDENCY_VERSION = "some-version";
    private static final Dependency DEPENDENCY_WITH_VERSION = new DependencyContainer(DEPENDENCY_NAME, DEPENDENCY_VERSION);
    private final String name;
    private final String version;
    private final boolean isVersionPresent;
    private final Dependency referenceDependency;
    private final boolean equals;

    public DependencyContainerTest(String name, String version, boolean isVersionPresent, Dependency referenceDependency, boolean equals) {
        this.name = name;
        this.version = version;
        this.isVersionPresent = isVersionPresent;
        this.referenceDependency = referenceDependency;
        this.equals = equals;
    }

    @Parameterized.Parameters(
            name = "name = {0}, version = {1} should have version present = {2} and must be " +
                    "equal = {4} to {3}"
    )
    public static Collection combinations() {
        return Arrays.asList(new Object[][]{
                {DEPENDENCY_NAME, DEPENDENCY_VERSION, true, DEPENDENCY_WITH_VERSION, true},
                {DEPENDENCY_NAME, null, false, DEPENDENCY_WITH_VERSION, true},
                {DEPENDENCY_NAME, null, false, DEPENDENCY_WITHOUT_VERSION, true},
                {DEPENDENCY_NAME, "any-version", true, DEPENDENCY_WITHOUT_VERSION, true},
                {"another-name", "another-version", true, DEPENDENCY_WITH_VERSION, false}
        });
    }

    @Test
    public void testIsVersionPresent() {
        assertEquals(isVersionPresent, new DependencyContainer(name, version).getVersion().isPresent());
    }

    @Test
    public void testEqualsToReference() {
        assertEquals(equals, referenceDependency.equals(new DependencyContainer(name, version)));
    }

}
