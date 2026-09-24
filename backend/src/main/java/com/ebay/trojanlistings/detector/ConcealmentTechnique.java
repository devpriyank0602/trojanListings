package com.ebay.trojanlistings.detector;

/** How a hostile instruction is hidden from a human reader. Named in verdicts (FR-013). */
public enum ConcealmentTechnique {
    /** Zero-width codepoints spliced inside words so no literal token matches. */
    ZERO_WIDTH,
    /** Cyrillic or Greek characters substituted into otherwise-Latin words. */
    HOMOGLYPH,
    /** Base64 blocks or s p a c e d letters the model decodes but a regex does not. */
    ENCODED_PAYLOAD,
    /** Counterfeit chat-template delimiters imitating the start of a new turn. */
    CHAT_TEMPLATE,
    /** Markup that hides text from a buyer while leaving it legible to a model. */
    INVISIBLE_MARKUP,
    /** No concealment -- a plain-text attack. */
    NONE
}
