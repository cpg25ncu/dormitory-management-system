package io.github.cpg25ncu.dormitory.housing;

/** Which students a building houses. Rooms in every building are single-gender. */
public enum GenderPolicy {
    MIXED(null), FEMALE_ONLY(Gender.FEMALE), MALE_ONLY(Gender.MALE);

    private final Gender only;

    GenderPolicy(Gender only) {
        this.only = only;
    }

    /** The only gender this building houses, or null for a mixed building. */
    Gender only() {
        return only;
    }

    boolean admits(Gender gender) {
        return only == null || only == gender;
    }
}
