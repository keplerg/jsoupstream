package org.jsoupstream.selector;

/**
 * Represents a token returned from the lexer
 */
public class Token
{

    // The Token type (simplified for streaming parser)
    public static enum Type
    {
        EOF,
        TAG,
        ATTRIBUTE_NAME,
        ATTRIBUTE_COMPARATOR,
        ATTRIBUTE_VALUE,
        PSEUDO,
        COMBINATOR,
        CLASS,
        ID,
        ACTION,
        ACTION_ARGUMENT,
        END_SELECTOR,
        UNKNOWN
    }

    public final Type type;
    public final byte[] text;

    public Token(byte[] s, Type t)
    {
        this.text = s;
        this.type = t;
    }

    public String toString()
    {
        return new String( text );
    }
}
