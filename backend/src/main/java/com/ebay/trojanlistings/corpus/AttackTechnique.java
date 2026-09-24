package com.ebay.trojanlistings.corpus;

/** How a hostile instruction reaches the agent. Primary reporting axis (FR-001). */
public enum AttackTechnique {
    /** Plain instruction in the title or description. */
    FREE_TEXT,
    /** Instruction planted in an item specific rather than the description. */
    STRUCTURED_FIELD,
    /** Instruction concealed to defeat literal keyword matching. */
    OBFUSCATED,
    /** Instruction rendered into the listing photo, absent from every text field. */
    IN_IMAGE
}
