package org.jsoupstream.selector;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/**
 * Lexical analyzer for a subset of CSS 3 selectors
 * 
 * <table>
 * <thead>
 * <tr>
 * <th>Pattern</th><th>Meaning</th>
 * </thead>
 * <tbody>
 * <tr><td>*</td><td>any element</td></tr>
 * <tr><td>E</td><td>an element of type E</td></tr>
 * <tr><td>E[foo]</td><td>an E element with a "foo" attribute</td></tr>
 * <tr><td>E[foo="bar"]</td><td>an E element whose "foo" attribute value is exactly equal to "bar"</td></tr>
 * <tr><td>E[foo~="bar"]</td><td>an E element whose "foo" attribute value is a list of whitespace-separated values, one of which is exactly equal to "bar"</td></tr>
 * <tr><td>E[foo^="bar"]</td><td>an E element whose "foo" attribute value begins exactly with the string "bar"</td></tr>
 * <tr><td>E[foo$="bar"]</td><td>an E element whose "foo" attribute value ends exactly with the string "bar"</td></tr>
 * <tr><td>E[foo*="bar"]</td><td>an E element whose "foo" attribute value contains the substring "bar"</td></tr>
 * <tr><td>E[foo|="en"]</td><td>an E element whose "foo" attribute has a hyphen-separated list of values beginning (from the left) with "en"</td></tr>
 * <tr><td>E:root</td><td>an E element, root of the document</td></tr>
 * <tr><td>E:nth-child(n)</td><td>an E element, the n-th child of its parent</td></tr>
 * <tr><td>E:first-child</td><td>an E element, first child of its parent</td></tr>
 * <tr><td>E:start(n)</td><td>appies to whole selector, how many matches before starting executing actions</td></tr>
 * <tr><td>E:count(n)</td><td>appies to whole selector, how many times to execute actions</td></tr>
 * <tr><td>E#myid</td><td>an E element with ID equal to "myid"</td></tr>
 * <tr><td>E F</td><td>an F element descendant of an E element</td></tr>
 * <tr><td>E > F</td><td>an F element child of an E element</td></tr>
 * <tr><td>E + F</td><td>an F element immediately preceded by an E element</td></tr>
 * <tr><td>E ~ F</td><td>an F element preceded by an E element</td></tr>
 * </tbody>
 * </table>
 */
public class Lexer
{
    private static final int BUFSIZ = 4096;
    private static enum State {
        BEGIN,
        IN_LIMBO,
        IN_TAG,
        IN_ID,
        IN_CLASS,
        IN_ATTRIBUTE_NAME,
        IN_ATTRIBUTE_COMPARATOR,
        IN_ATTRIBUTE_VALUE,
        IN_COMBINATOR,
        IN_PSEUDO,
        IN_ACTION,
        IN_ACTION_ARGUMENT,
        END,
        EOF
    }

    private InputStream in;
    private byte[] buffer = new byte[BUFSIZ];
    private byte current_quote = ' ';
    private byte pending_character = ' ';
    private int pos = 0; // where currently positioned in the buffer
    private int line = 1; // keep track of current line
    private State state = State.BEGIN;
    private boolean last_was_space = false;

    public Lexer(InputStream input)
    {
        this.in = input;
        if ( ! in.markSupported() )
        {
            this.in = new BufferedInputStream( this.in );
        }
    }

    public Lexer(String input)
    {
        this.in = new ByteArrayInputStream( input.getBytes(StandardCharsets.UTF_8) );
        if ( ! in.markSupported() )
        {
            this.in = new BufferedInputStream( this.in );
        }
    }

    public Lexer(String input, Charset charset)
    {
        this.in = new ByteArrayInputStream( input.getBytes( charset ) );
        if ( ! in.markSupported() )
        {
            this.in = new BufferedInputStream( this.in );
        }
    }

    public Token advance() throws IOException
    {
        try
        {
            pos = 0;

            switch ( state )
            {
            case END:
                advanceWhitespace();
                if ( state == State.EOF )
                {
                    return new Token( "".getBytes(), Token.Type.EOF );
                }
                else
                {
                    state = State.BEGIN;
                    return new Token( "".getBytes(), Token.Type.END_SELECTOR );
                }

            case EOF:
                return new Token( "".getBytes(), Token.Type.EOF );

            case IN_TAG:
                return getTag();

            case IN_ID:
                return getName( State.IN_LIMBO, Token.Type.ID );

            case IN_CLASS:
                return getName( State.IN_LIMBO, Token.Type.CLASS );

            case IN_ATTRIBUTE_NAME:
                return getName( State.IN_ATTRIBUTE_COMPARATOR, Token.Type.ATTRIBUTE_NAME );

            case IN_ATTRIBUTE_VALUE:
                return getAttributeValue();

            case IN_PSEUDO:
                return getPseudo();

            case IN_ACTION:
                return getAction();

            case IN_ACTION_ARGUMENT:
                return getActionArgument();

            default:
                int ch = in.read();
                if ( ch < 0 )
                {
                    state = State.EOF;
                    return new Token( "".getBytes(), Token.Type.EOF );
                }
                buffer[pos++] = (byte)ch;

                if ( Character.isWhitespace( ch ) )
                {
                    if ( (char)ch == '\n' )
                    {
                        line++;
                    }

                    // Whitespace is ignored
                    advanceWhitespace();
                    last_was_space = true;
                    return advance();
                }

                switch ( ch )
                {
                case '/':
                    peek(1);

                    if ( buffer[1] == '*' )
                    {
                        // Comments are ignored
                        advanceComment();
                        last_was_space = false;
                        return advance();
                    }
                    else
                    {
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }

                case '#':
                    if ( state == State.BEGIN || state == State.IN_LIMBO )
                    {
                        if ( last_was_space && state != State.IN_COMBINATOR && state != State.BEGIN )
                        {
                            // Change state but don't return token
                            state = State.IN_ID;
                            last_was_space = false;
                            return new Token( " ".getBytes(), Token.Type.COMBINATOR );
                        }
                        // Change state but don't return token
                        state = State.IN_ID;
                        last_was_space = false;
                        return advance();
                    }
                    else
                    {
                        // Syntax error
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }

                case '.':
                    if ( state == State.BEGIN || state == State.IN_LIMBO )
                    {
                        if ( last_was_space && state != State.IN_COMBINATOR && state != State.BEGIN )
                        {
                            // Change state but don't return token
                            state = State.IN_CLASS;
                            last_was_space = false;
                            return new Token( " ".getBytes(), Token.Type.COMBINATOR );
                        }
                        // Change state but don't return token
                        state = State.IN_CLASS;
                        last_was_space = false;
                        return advance();
                    }
                    else
                    {
                        // Syntax error
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }

                case '~':
                case '^':
                case '$':
                case '*':
                case '|':
                case '=':
                    switch ( state )
                    {
                    case IN_ATTRIBUTE_COMPARATOR:
                        if ( buffer[0] == '=' )
                        {
                            state = State.IN_ATTRIBUTE_VALUE;
                            last_was_space = false;
                            return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.ATTRIBUTE_COMPARATOR );
                        }

                        peek(1);

                        if ( buffer[1] == '=' )
                        {
                            pos += in.read( buffer, pos, 1 );
                            state = State.IN_ATTRIBUTE_VALUE;
                            last_was_space = false;
                            return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.ATTRIBUTE_COMPARATOR );
                        }
                        else
                        {
                            return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                        }

                    case IN_LIMBO:
                        switch( buffer[0] )
                        {
                        case '*':
                            if ( last_was_space && state != State.IN_COMBINATOR && state != State.BEGIN )
                            {
                                pending_character = buffer[0];
                                state = State.IN_TAG;
                                last_was_space = false;
                                return new Token( " ".getBytes(), Token.Type.COMBINATOR );
                            }
                            last_was_space = false;
                            return getTag();
                        case '~':
                            state = State.IN_COMBINATOR;
                            last_was_space = false;
                            return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.COMBINATOR );
                        default:
                            return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                        }

                    case BEGIN:
                        switch( buffer[0] )
                        {
                        case '*':
                            last_was_space = false;
                            return getTag();
                        default:
                            return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                        }

                    default:
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }

                case '[':
                    if ( state == State.BEGIN || state == State.IN_LIMBO )
                    {
                        if ( last_was_space && state != State.IN_COMBINATOR && state != State.BEGIN )
                        {
                            // Change state but don't return token
                            state = State.IN_ATTRIBUTE_NAME;
                            last_was_space = false;
                            return new Token( " ".getBytes(), Token.Type.COMBINATOR );
                        }
                        // Change state but don't return token
                        state = State.IN_ATTRIBUTE_NAME;
                        last_was_space = false;
                        return advance();
                    }
                    else
                    {
                        // Syntax error
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }

                case ']':
                    if ( state == State.IN_ATTRIBUTE_COMPARATOR || state == State.IN_LIMBO )
                    {
                        // Change state but don't return token
                        state = State.IN_LIMBO;
                        last_was_space = false;
                        return advance();
                    }
                    else
                    {
                        // Syntax error
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }

                case ':':
                    if ( last_was_space && state != State.IN_COMBINATOR && state != State.BEGIN )
                    {
                        // Change state but don't return token
                        state = State.IN_PSEUDO;
                        last_was_space = false;
                        return new Token( " ".getBytes(), Token.Type.COMBINATOR );
                    }
                    state = State.IN_PSEUDO;
                    last_was_space = false;
                    return advance();

                case '{':
                    // Change state but don't return token
                    state = State.IN_ACTION;
                    last_was_space = false;
                    return advance();

                case '}':
                    // Change state but don't return token
                    state = State.END;
                    last_was_space = false;
                    return advance();

                case '(':
                    if ( state != State.IN_ACTION )
                    {
                        // Syntax error
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }
                    else
                    {
                        // Change state but don't return token
                        state = State.IN_ACTION_ARGUMENT;
                        last_was_space = false;
                        return advance();
                    }

                case '+':
                    if ( state == State.IN_LIMBO )
                    {
                        state = State.IN_COMBINATOR;
                        last_was_space = false;
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.COMBINATOR );
                    }
                    else
                    {
                        // Syntax error
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }

                case '>':
                    if ( state == State.IN_LIMBO )
                    {
                        state = State.IN_COMBINATOR;
                        last_was_space = false;
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.COMBINATOR );
                    }
                    else
                    {
                        // Syntax error
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }

                default:
                    if ( state == State.BEGIN || state == State.IN_COMBINATOR || state == State.IN_LIMBO )
                    {
                        if ( last_was_space && state != State.IN_COMBINATOR && state != State.BEGIN )
                        {
                            pending_character = buffer[0];
                            state = State.IN_TAG;
                            last_was_space = false;
                            return new Token( " ".getBytes(), Token.Type.COMBINATOR );
                        }
                        last_was_space = false;
                        return getTag();
                    }
                    else
                    {
                        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
                    }
                }
            }
        }
        catch (Exception e)
        {
        }

        return new Token( "".getBytes(), Token.Type.UNKNOWN );
    }

    public int getLine()
    {
        return this.line;
    }

    public boolean skipTo(String val)
    {
        while ( state != State.EOF )
        {
            // gobble up input until match found
        }
        return false; // not found or eof
    }

    private int compareStringToBuffer( String s )
    {
        for (int i = 0; i < s.length(); i++)
        {
            if ( s.charAt(i) != buffer[i] )
            {
                return ( s.charAt(i) - buffer[i] );
            }
        }

        return 0;
    }

    private int peek(int how_far) throws IOException
    {
        int num_read;

        if ( pos >= (BUFSIZ - 1) )
        {
            return 0;
        }

        if ( (pos + how_far) >= BUFSIZ )
        {
            how_far = BUFSIZ - pos - 1;
        }

        in.mark(how_far);
        num_read = in.read(buffer, pos, how_far);
        if ( num_read >= 0 )
        {
            in.reset();
        }

        return num_read;
    }

    private void advanceWhitespace() throws IOException
    {
        int ch;

        last_was_space = false;
        while ( true )
        {
            peek(1);

            if ( ! Character.isWhitespace( (char)buffer[pos] ) )
            {
                if ( (char)buffer[pos] == '/' )
                {
                    peek(2);

                    if ( (char)buffer[pos+1] == '*' )
                    {
                        // Comments are ignored
                        ch = in.read(); // '/'
                        ch = in.read(); // '*'
                        pos = 0;
                        advanceComment();
                        continue;
                    }
                }
                else
                {
                    return;
                }
            }

            ch = in.read();
            pos = 0;
            if ( ch < 0 )
            {
                state = State.EOF;
                break;
            }
            else if ( (char)ch == ' ' )
            {
                last_was_space = true;
            }
            else if ( (char)ch == '\n' )
            {
                line++;
            }
        }

        return;
    }

    private void advanceComment() throws IOException
    {
        int ch;

        while ( true )
        {
            ch = in.read();
            if ( ch < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( (char)ch == '*' )
            {
                peek(1);

                if ( buffer[pos] == '/' )
                {
                    ch = in.read();
                    pos = 0;
                    return;
                }
            }
            else if ( (char)ch == '\n' )
            {
                line++;
            }

        }

        return;
    }

    private void advanceString(byte quote) throws IOException
    {
        int ch;
        int buf_end = BUFSIZ - 1;

        while ( pos < buf_end )
        {
            ch = in.read();
            if ( ch < 0 )
            {
                state = State.EOF;
                break;
            }
            buffer[pos++] = (byte)ch;

            if ( (char)ch == quote )
            {
                return;
            }
            else if ( (char)ch == '\n' )
            {
                line++;
            }

        }

        return;
    }

    private Token getTag() throws IOException
    {
        int num_read;

        if ( pending_character != ' ' )
        {
            buffer[pos++] = pending_character;
            pending_character = ' ';
        }

        while ( pos < buffer.length )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            // if ( ! Character.isLetterOrDigit( (char)buffer[pos] ) && buffer[pos] != '|' )
            if ( ! Character.isLetterOrDigit( (char)buffer[pos] ) )
            {
                state = State.IN_LIMBO;
                break;
            }
            buffer[pos++] = (byte)in.read();
        }

        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.TAG);
    }

    private Token getName( State new_state, Token.Type type ) throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 1;

        advanceWhitespace();
        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( Character.isWhitespace( (char)buffer[pos] ) || "#.:[]{}()=+~>\"'^$*|".indexOf( buffer[pos] ) >= 0 )
            {
                state = new_state;
                return new Token( Arrays.copyOfRange(buffer, 0, pos), type );
            }
            buffer[pos++] = (byte)in.read();
        }

        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
    }

    private Token getAttributeValue() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 1;

        advanceWhitespace();
        num_read = peek(1);
        if ( num_read < 0 )
        {
            state = State.EOF;
            return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
        }

        if ( buffer[pos] == '"' || buffer[pos] == '\'' || current_quote != ' ' )
        {
            if ( current_quote == ' ' )
            {
                current_quote = buffer[pos];
            }
            buffer[pos++] = (byte)in.read();
            advanceString( current_quote );
            if ( buffer[pos-1] == current_quote )
            {
                current_quote = ' ';
            }
            state = State.IN_LIMBO;
            return new Token( Arrays.copyOfRange(buffer, 1, pos-1), Token.Type.ATTRIBUTE_VALUE );
        }

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( Character.isWhitespace( (char)buffer[pos] ) || buffer[pos] == ']' )
            {
                state = State.IN_LIMBO;
                return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.ATTRIBUTE_VALUE );
            }
            buffer[pos++] = (byte)in.read();
        }

        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
    }

    private Token getAction() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 1;

        advanceWhitespace();
        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( buffer[pos] == ';' )
            {
                in.read();
                if ( pos > 1 )
                {
                    return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.ACTION );
                }
                else
                {
                    return advance();
                }
            }
            else if ( buffer[pos] == '}' )
            {
                in.read();
                state = State.END;
                if ( pos > 0 )
                {
                    return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.ACTION );
                }
                else
                {
                    return advance();
                }
            }
            else if ( Character.isWhitespace( (char)buffer[pos] ) || buffer[pos] == '(' )
            {
                in.read();
                state = State.IN_ACTION_ARGUMENT;
                if ( pos > 0 )
                {
                    return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.ACTION );
                }
            }
            buffer[pos++] = (byte)in.read();
        }

        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
    }

    private Token getActionArgument() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 1;

        advanceWhitespace();
        num_read = peek(1);
        if ( num_read < 0 )
        {
            state = State.EOF;
            return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
        }

        if ( buffer[pos] == '"' || buffer[pos] == '\'' || current_quote != ' ' )
        {
            if ( current_quote == ' ' )
            {
                current_quote = buffer[pos];
            }
            buffer[pos++] = (byte)in.read();
            advanceString( current_quote );
            if ( buffer[pos-1] == current_quote )
            {
                current_quote = ' ';
            }
            return new Token( Arrays.copyOfRange(buffer, 1, pos-1), Token.Type.ACTION_ARGUMENT );
        }

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( buffer[pos] == '(' )
            {
                in.read();
                return advance();
            }
            else if ( buffer[pos] == ')' )
            {
                in.read();
                state = State.IN_ACTION;
                if ( pos > 0 )
                {
                    return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.ACTION_ARGUMENT );
                }
                else
                {
                    return advance();
                }
            }
            else if ( buffer[pos] == '}' )
            {
                in.read();
                state = State.END;
                if ( pos > 0 )
                {
                    return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.ACTION_ARGUMENT );
                }
                else
                {
                    return advance();
                }
            }
            else if ( buffer[pos] == ',' )
            {
                in.read();
                if ( pos > 0 )
                {
                    return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.ACTION_ARGUMENT );
                }
                else
                {
                    return advance();
                }
            }
            else if ( Character.isWhitespace( buffer[pos] ) )
            {
                return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
            }
            buffer[pos++] = (byte)in.read();
        }

        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.UNKNOWN );
    }

    private Token getPseudo() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 1;
        boolean ignore_whitespace = false;

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( ! Character.isLetterOrDigit( (char)buffer[pos] )
                && "()+-".indexOf( buffer[pos] ) < 0
                && ( ! Character.isWhitespace( buffer[pos]) || ! ignore_whitespace ) )
            {
                state = State.IN_LIMBO;
                return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.PSEUDO );
            }
            else if ( buffer[pos] == '(' )
            {
                ignore_whitespace = true;
            }
            else if ( buffer[pos] == ')' )
            {
                ignore_whitespace = false;
            }
            if ( ! Character.isWhitespace( (char)buffer[pos] ) )
            {
                buffer[pos++] = (byte)in.read();
            }
            else
            {
                in.read();
            }
        }

        return new Token( Arrays.copyOfRange(buffer, 0, pos), Token.Type.PSEUDO );
    }
}
