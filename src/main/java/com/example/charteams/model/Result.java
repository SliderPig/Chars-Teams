package com.example.charteams.model;

// How a team action went.
public enum Result {
    OK,
    ALREADY_ON_TEAM,
    NOT_ON_TEAM,
    NOT_OWNER,
    BAD_NAME,
    NAME_TAKEN,
    BAD_COLOUR,
    TEAM_FULL,
    SELF,
    NOT_A_MEMBER,
    ALREADY_MEMBER,
    TARGET_HAS_TEAM,
    ALREADY_INVITED,
    ASK_LATER,
    TOO_MANY_INVITES,
    NO_INVITE
}
