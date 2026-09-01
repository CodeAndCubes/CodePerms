package com.mrleonardos.codeperms.api.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mrleonardos.codeperms.api.model.ContextSet;
import com.mrleonardos.codeperms.api.model.NodeEntry;

class ResolutionTest {

    private static final ContextSet NO_CONTEXTS = ContextSet.empty();
    private static final ContextSet NETHER = context("world", "nether");

    @Test
    void noCandidatesMeansDenial() {
        Resolution resolution = Resolution.none();
        assertFalse(resolution.allowed());
        assertFalse(
            resolution.winner()
                .isPresent());
        assertTrue(
            resolution.candidates()
                .isEmpty());
        assertEquals(Resolution.none(), resolution);
    }

    @Test
    void singleCandidateDecides() {
        Resolution resolution = Resolution
            .of(candidates(Candidate.of("player", NodeEntry.allow("codechat.create"), NO_CONTEXTS)));
        assertTrue(resolution.allowed());
        assertEquals(
            "player",
            resolution.winner()
                .get()
                .source());
        assertEquals(
            1,
            resolution.candidates()
                .size());
    }

    @Test
    void moreContextMatchesWin() {
        List<Candidate> found = candidates(
            Candidate.of("group:vip", NodeEntry.deny("codechat.format"), NETHER),
            Candidate.of("group:vip", NodeEntry.of("codechat.format", true, NETHER, 0L), NETHER));
        Resolution resolution = Resolution.of(found);
        assertTrue(resolution.allowed());
        assertEquals(
            1,
            resolution.winner()
                .get()
                .contextMatches());
    }

    @Test
    void precisionBreaksTheTie() {
        List<Candidate> found = candidates(
            Candidate.of("group:vip", NodeEntry.allow("codechat.channel.*"), NO_CONTEXTS),
            Candidate.of("group:vip", NodeEntry.deny("codechat.channel.staff.read"), NO_CONTEXTS));
        Resolution resolution = Resolution.of(found);
        assertFalse(resolution.allowed());
        assertEquals(
            "codechat.channel.staff.read",
            resolution.winner()
                .get()
                .entry()
                .node());
    }

    @Test
    void denialBeatsPermissionAtEqualPrecision() {
        List<Candidate> found = candidates(
            Candidate.of("group:vip", NodeEntry.allow("codechat.create"), NO_CONTEXTS),
            Candidate.of("group:vip", NodeEntry.deny("codechat.create"), NO_CONTEXTS));
        Resolution resolution = Resolution.of(found);
        assertFalse(resolution.allowed());
        assertTrue(
            resolution.winner()
                .get()
                .deny());
        assertEquals(
            2,
            resolution.candidates()
                .size());
        assertTrue(
            resolution.candidates()
                .get(0)
                .deny());
    }

    @Test
    void emptyCandidateListIsDenied() {
        assertFalse(
            Resolution.of(candidates())
                .allowed());
    }

    private static ContextSet context(String key, String value) {
        return ContextSet.builder()
            .put(key, value)
            .build();
    }

    private static List<Candidate> candidates(Candidate... values) {
        List<Candidate> list = new ArrayList<>();
        for (Candidate value : values) {
            list.add(value);
        }
        return list;
    }
}
