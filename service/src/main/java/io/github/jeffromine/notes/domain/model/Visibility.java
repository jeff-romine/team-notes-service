package io.github.jeffromine.notes.domain.model;

/** Who may read a note. PRIVATE: the owner. TEAM: members of the note's team. ORGANIZATION: every signed-in user. */
public enum Visibility { PRIVATE, TEAM, ORGANIZATION }
