package com.example.domain;

import java.util.Set;
import java.util.Objects;

public class Group {
    private final String id;
    private final String name;
    private final Set<String> courseNames;

    public Group(String id, String name, Set<String> courseNames) {
        this.id = id;
        this.name = name;
        this.courseNames = courseNames;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<String> getCourseNames() {
        return courseNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Group group = (Group) o;
        return Objects.equals(id, group.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
