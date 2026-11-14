package ca.inspection.home.inspection.DTO;

import java.util.UUID;

// One photo in a booking's photo list: all the picker and the unused-photo tally read.
public record ImageSummary(UUID id, String imageUrl, Boolean used) {}
