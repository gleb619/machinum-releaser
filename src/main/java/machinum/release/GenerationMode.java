package machinum.release;

/**
 * Enumeration defining different generation modes for release schedules.
 * 
 * LEGACY: Current implementation with wave/peak pattern generation
 * ANNUITY: Equal chunks distribution with configurable start/end percentages
 */
public enum GenerationMode {
    /**
     * Legacy implementation that generates schedules using wave/peak patterns
     * with configurable bulk factors, smooth factors, and randomization.
     */
    LEGACY,
    
    /**
     * Annuity-style generation that splits chapters into equal chunks.
     * Allows control over start/end percentages and minimum chapters per chunk.
     */
    ANNUITY
}