package org.jsoupstream;

import java.util.List;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Lexical analyzer for HTML. Returns a token each time advance() is called.
 */
public class HtmlLexer
{
    private static final int BUFSIZ = 4096;
    private static enum State {
        BEGIN,
        IN_PROCESSING_INSTRUCTION,
        IN_DOCTYPE,
        IN_OPEN_TAG,
        IN_TAG,
        IN_ATTRIBUTE_NAME,
        IN_ATTRIBUTE_VALUE,
        IN_CLOSE_TAG,
        IN_COMMENT,
        IN_COMMENT_END,
        IN_TEXT,
        IN_CDATA,
        IN_CDATA_END,
        EOF
    }

    private InputStream in;
    private Charset charset = StandardCharsets.UTF_8;
    private byte[] buffer = new byte[BUFSIZ];
    private int pos = 0; // where currently positioned in the buffer
    private byte current_quote = ' ';
    private State state = State.BEGIN;

    public HtmlLexer(String html, Charset charset)
    {
        this.charset = charset;
        this.in = new ByteArrayInputStream( html.getBytes( charset ) );
    }

    public void setCharset(Charset charset)
    {
        this.charset = charset;
        HtmlToken.setCharset( charset );
    }

    public HtmlLexer(InputStream in)
    {
        if ( ! in.markSupported() )
        {
            this.in = new BufferedInputStream( in );
        }
        else
        {
            this.in = in;
        }
    }

    public HtmlToken advance()
    {
        try
        {
            pos = 0;

            switch ( state )
            {
            case EOF:
                return HtmlToken.getToken( null, 0, 0, HtmlToken.Type.EOF, charset );

            case IN_PROCESSING_INSTRUCTION:
                return getProcessingInstruction();

            case IN_DOCTYPE:
                return getDocType();

            case IN_COMMENT:
                return getComment();

            case IN_COMMENT_END:
                pos += this.read( buffer, pos, 3 );
                state = State.IN_TEXT;
                return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.END_COMMENT, charset );

            case IN_CDATA:
                return getCdata();

            case IN_CDATA_END:
                pos += this.read( buffer, pos, 3 );
                state = State.IN_TEXT;
                return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.END_CDATA, charset );

            case IN_OPEN_TAG:
            case IN_CLOSE_TAG:
                return getTag();

            case IN_ATTRIBUTE_NAME:
                return getAttributeName();

            case IN_ATTRIBUTE_VALUE:
                // attribute value may be returned in multiple chunks if > BUFSIZ
                return getAttributeValue();

            default:
                int ch = this.read();
                if ( ch < 0 )
                {
                    state = State.EOF;
                    return HtmlToken.getToken( null, 0, 0, HtmlToken.Type.EOF, charset );
                }
                buffer[pos++] = (byte)ch;

                if ( Character.isWhitespace( ch ) )
                {
                    return getWhitespace();
                }

                switch ( ch )
                {
                case '<':
                    peek(3);

                    switch ( buffer[1] )
                    {
                    case '!':
                        if ( buffer[2] == '-' && buffer[3] == '-' )
                        {
                            pos += this.read( buffer, pos, 3 );
                            state = State.IN_COMMENT;
                            return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.START_COMMENT, charset );
                        }
                        else if ( peek(8) == 8 && compareStringToBuffer( "<![CDATA[" ) == 0 )
                        {
                            pos += this.read( buffer, pos, 8 );
                            state = State.IN_CDATA;
                            return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.START_CDATA, charset );
                        }
                        else 
                        {
                            state = State.IN_DOCTYPE;
                            return getDocType();
                        }
                    case '/':
                        pos += this.read( buffer, pos, 1 );
                        state = State.IN_CLOSE_TAG;
                        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.OPEN_END_TAG, charset );
                    case '?':
                        state = State.IN_PROCESSING_INSTRUCTION;
                        return getProcessingInstruction();
                    default:
                        state = State.IN_OPEN_TAG;
                        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.OPEN_TAG, charset );
                    }
                case '/':
                    peek(1);

                    if ( buffer[1] == '>' )
                    {
                        pos += this.read( buffer, pos, 1 );
                        state = State.IN_TEXT;
                        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.END_SELF_CLOSING_TAG, charset );
                    }
                    else
                    {
                        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.TEXT, charset );
                    }
                case '=':
                    if ( state == State.IN_TAG )
                    {
                        state = State.IN_ATTRIBUTE_VALUE;
                    }

                    return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.EQUALS, charset );
                case '>':
                    state = State.IN_TEXT;
                    return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.CLOSE_TAG, charset );
                default:
                    if ( state == State.IN_TEXT )
                    {
                        return getText();
                    }

                    return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.UNKNOWN, charset );
                }
            }
        }
        catch (Exception e)
        {
        }

        return HtmlToken.getToken( null, 0, pos, HtmlToken.Type.UNKNOWN, charset );
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

    private int read() throws IOException
    {
        return in.read();
    }

    public int read( byte[] buffer, int offset, int num ) throws IOException
    {
        return in.read( buffer, offset, num );
    }


    private void reset( ) throws IOException
    {
        in.reset();
    }

    private void mark( int readLimit )
    {
        in.mark( readLimit );
    }


    private int peek(int how_far) throws IOException
    {
        int num_read;

        if ( pos >= (buffer.length - 1) )
        {
            return 0;
        }

        if ( (pos + how_far) >= buffer.length )
        {
            how_far = buffer.length - pos - 1;
        }

        this.mark(how_far);
        num_read = this.read(buffer, pos, how_far);
        if ( num_read >= 0 )
        {
            this.reset();
        }

        return num_read;
    }

    private void advanceTo( String str, boolean inclusive, State state ) throws IOException
    {
        int num_read;
        byte[] bytes = str.getBytes( charset );
        int buf_end = buffer.length - bytes.length;

        while ( state != State.EOF )
        {
            while ( pos < buf_end )
            {
                num_read = peek( bytes.length );
                if ( num_read < 0 )
                {
                    state = State.EOF;
                    break;
                }

                if ( num_read == bytes.length && buffer[pos] == bytes[0] )
                {
                    boolean match = true;
                    for ( int i = 1; i < bytes.length; i++ )
                    {
                        if ( buffer[pos+i] != bytes[i] )
                        {
                            match = false;
                            break;
                        }
                    }
                    if ( match )
                    {
                        if ( inclusive )
                        {
                            for ( int i = 0; i < bytes.length; i++ )
                            {
                                buffer[pos++] = (byte)this.read();
                            }
                        }
                        this.state = state;
                        return;
                    }
                }
                buffer[pos++] = (byte)this.read();
            }

            // reallocate a larger buffer for this token
            buffer = Arrays.copyOfRange( buffer, 0, (buffer.length + BUFSIZ) );
            buf_end = buffer.length - bytes.length;
        }

        state = State.EOF;
        return;
    }

    private HtmlToken getWhitespace() throws IOException
    {
        int num_read;
        int buf_end = buffer.length - 1;

        if ( state == State.IN_TEXT )
        {
            return getText();
        }

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( ! Character.isWhitespace( (char)buffer[pos] ) )
            {
                if ( state == State.IN_TAG && buffer[pos] != '=' && buffer[pos] != '/' && buffer[pos] != '>' )
                {
                    state = State.IN_ATTRIBUTE_NAME;
                }
                return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.WHITESPACE, charset );
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.WHITESPACE, charset );
    }

    private void advanceString(byte quote) throws IOException
    {
        int ch;
        int buf_end = buffer.length - 1;

        while ( pos < buf_end )
        {
            ch = this.read();
            if ( ch < 0 )
            {
                state = State.EOF;
                break;
            }
            buffer[pos++] = (byte)ch;

            if ( ch == quote )
            {
                return;
            }
        }

        return;
    }

    private HtmlToken getTag() throws IOException
    {
        int num_read;
        int buf_end = buffer.length - 1;

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( Character.isWhitespace( (char)buffer[pos] ) )
            {
                state = State.IN_TAG;
                return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.TAG_NAME, charset );
            }
            else if ( buffer[pos] == '/' || buffer[pos] == '>' )
            {
                state = State.IN_TEXT;
                return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.TAG_NAME, charset );
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.TAG_NAME, charset );
    }

    private HtmlToken getAttributeName() throws IOException
    {
        int num_read;
        int buf_end = buffer.length - 1;

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( Character.isWhitespace( (char)buffer[pos] ) || buffer[pos] == '=' )
            {
                state = State.IN_TAG;
                return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.ATTRIBUTE_NAME, charset );
            }
            else if ( buffer[pos] == '>' )
            {
                state = State.IN_TEXT;
                return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.ATTRIBUTE_NAME, charset );
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.ATTRIBUTE_NAME, charset );
    }

    private HtmlToken getAttributeValue() throws IOException
    {
        int num_read;
        int buf_end = buffer.length - 1;

        num_read = peek(1);
        if ( num_read < 0 )
        {
            state = State.EOF;
            return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.UNKNOWN, charset );
        }

        if ( Character.isWhitespace( (char)buffer[pos] ) )
        {
            return getWhitespace();
        }

        if ( buffer[pos] == '"' || buffer[pos] == '\'' || current_quote != ' ' )
        {
            if ( current_quote == ' ' )
            {
                current_quote = buffer[pos];
            }
            buffer[pos++] = (byte)this.read();
            advanceString( current_quote );
            if ( buffer[pos-1] == current_quote )
            {
                current_quote = ' ';
                state = State.IN_TAG;
            }
            return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.ATTRIBUTE_VALUE, charset );
        }

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( Character.isWhitespace( (char)buffer[pos] ) )
            {
                state = State.IN_TAG;
                return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.ATTRIBUTE_VALUE, charset );
            }
            else if ( buffer[pos] == '>' )
            {
                state = State.IN_TEXT;
                return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.ATTRIBUTE_VALUE, charset );
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.ATTRIBUTE_VALUE, charset );
    }

    private HtmlToken getComment() throws IOException
    {
        advanceTo( "-->", false, State.IN_COMMENT_END );
        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.COMMENT, charset );
    }

    private HtmlToken getCdata() throws IOException
    {
        advanceTo( "]]>", false, State.IN_CDATA_END );
        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.CDATA, charset );
    }

    private HtmlToken getProcessingInstruction() throws IOException
    {
        advanceTo( ">", true, State.IN_TEXT );
        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.PROCESSING_INSTRUCTION, charset );
    }

    private HtmlToken getDocType() throws IOException
    {
        advanceTo( ">", true, State.IN_TEXT );
        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.DOCTYPE, charset );
    }

    private HtmlToken getText() throws IOException
    {
        advanceTo( "<", false, State.IN_TEXT );
        return HtmlToken.getToken( buffer, 0, pos, HtmlToken.Type.TEXT, charset );
    }
}
