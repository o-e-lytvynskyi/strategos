package dev.strategos.devtools.demo;

import dev.strategos.core.content.TemplateSet;
import dev.strategos.core.shared.TemplateId;

/** The templates the chapters use. */
final class Ids {
    static final TemplateId WORKER = TemplateSet.WORKER;
    static final TemplateId INFANTRY = TemplateSet.INFANTRY;
    static final TemplateId SHEEP = new TemplateId("strategos:unit/sheep");
    static final TemplateId PIG = new TemplateId("strategos:unit/pig");
    static final TemplateId CIVIC_CENTRE = new TemplateId("strategos:building/civic_centre");
    static final TemplateId STOREHOUSE = new TemplateId("strategos:building/storehouse");
    static final TemplateId FARMSTEAD = new TemplateId("strategos:building/farmstead");
    static final TemplateId FIELD = new TemplateId("strategos:building/field");
    static final TemplateId HOUSE = new TemplateId("strategos:building/house");
    static final TemplateId BARRACKS = new TemplateId("strategos:building/barracks");
    static final TemplateId IRON_MINE = new TemplateId("strategos:building/iron_mine");
    static final TemplateId QUARRY = new TemplateId("strategos:building/quarry");
    /** Gaia's slot in a two-faction match: the animals'. */
    static final int GAIA = 2;

    private Ids() {}
}
