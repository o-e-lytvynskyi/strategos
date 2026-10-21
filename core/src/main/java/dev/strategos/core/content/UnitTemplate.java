package dev.strategos.core.content;

import dev.strategos.core.shared.TemplateId;

/**
 * A unit type: {@code netIndex} is its index in the set, sent instead of the id; {@code bodyType} is the entity type of
 * its body; speed in blocks per tick; size in blocks.
 */
public record UnitTemplate(TemplateId id, int netIndex, String bodyType, double walkSpeed, float width, float height) {}
