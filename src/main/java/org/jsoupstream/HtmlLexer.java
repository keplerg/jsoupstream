package org.jsoupstream;

import java.util.List;
import java.util.ArrayList;
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
    private static final boolean USE_INTERNAL_READ = false;  // buggy - don't use
    private static final int IN_BUFSIZ = 8192;
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
    private byte[] readBuffer = new byte[IN_BUFSIZ];
    private byte[] buffer = new byte[BUFSIZ];
    private byte current_quote = ' ';
    private int pos = 0; // where currently positioned in the buffer
    private int in_pos = 0; // where currently positioned in the readBuffer
    private int in_size = 0;
    private int mark_pos = 0;
    private boolean in_mark = false;
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
        if ( ! USE_INTERNAL_READ && ! in.markSupported() )
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
                // Processing instruction may be returned in multiple chunks
                return getProcessingInstruction();

            case IN_DOCTYPE:
                // Doctype may be returned in multiple chunks
                return getDocType();

            case IN_COMMENT:
                // return up to ~BUFSIZ of comment
                // comment may be returned in multiple chunks
                return getComment();

            case IN_COMMENT_END:
                // need to send comment end sequence
                pos += this.read( buffer, pos, 3 );
                state = State.IN_TEXT;
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.END_COMMENT, charset );

            case IN_CDATA:
                // return up to ~BUFSIZ of data
                // data may be returned in multiple chunks
                return getCdata();

            case IN_CDATA_END:
                // need to send CDATA end sequence
                pos += this.read( buffer, pos, 3 );
                state = State.IN_TEXT;
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.END_CDATA, charset );

            case IN_OPEN_TAG:
            case IN_CLOSE_TAG:
                return getTag();

            case IN_ATTRIBUTE_NAME:
                return getAttributeName();

            case IN_ATTRIBUTE_VALUE:
                // attribute value may be returned in multiple chunks
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
                            return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.START_COMMENT, charset );
                        }
                        else if ( peek(8) == 8 && compareStringToBuffer( "<![CDATA[" ) == 0 )
                        {
                            pos += this.read( buffer, pos, 8 );
                            state = State.IN_CDATA;
                            return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.START_CDATA, charset );
                        }
                        else 
                        {
                            state = State.IN_DOCTYPE;
                            return getDocType();
                        }
                    case '/':
                        pos += this.read( buffer, pos, 1 );
                        state = State.IN_CLOSE_TAG;
                        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.OPEN_END_TAG, charset );
                    case '?':
                        state = State.IN_PROCESSING_INSTRUCTION;
                        return getProcessingInstruction();
                    default:
                        state = State.IN_OPEN_TAG;
                        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.OPEN_TAG, charset );
                    }
                case '/':
                    peek(1);

                    if ( buffer[1] == '>' )
                    {
                        pos += this.read( buffer, pos, 1 );
                        state = State.IN_TEXT;
                        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.END_SELF_CLOSING_TAG, charset );
                    }
                    else
                    {
                        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.TEXT, charset );
                    }
                case '=':
                    if ( state == State.IN_TAG )
                    {
                        state = State.IN_ATTRIBUTE_VALUE;
                    }

                    return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.EQUALS, charset );
                case '>':
                    state = State.IN_TEXT;
                    return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.CLOSE_TAG, charset );
                default:
                    if ( state == State.IN_TEXT )
                    {
                        return getText();
                    }

                    return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.UNKNOWN, charset );
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
        if ( USE_INTERNAL_READ )
        {
            if ( in_size < 0 )
            {
                return -1;
            }

            if ( in_pos >= in_size )
            {
                in_size = this.read( readBuffer, 0 , IN_BUFSIZ );
                if ( in_size < 0 )
                {
                    return -1;
                }
                in_pos = 0;
            }

            return readBuffer[in_pos++];
        }
        else
        {
            return in.read();
        }
    }

    public int read( byte[] buffer, int offset, int num ) throws IOException
    {
        if ( USE_INTERNAL_READ )
        {
            int count = 0;

            if ( in_size < 0 )
            {
                return -1;
            }

            while ( in_pos < in_size && count < num )
            {
                buffer[offset++] = readBuffer[in_pos++];
                count++;
            }
    
            if ( in_pos >= in_size )
            {
                if ( in_mark )
                {
                    // shift the data left to allow more room
                    for ( int i = 0; i < (in_pos - mark_pos); i++ )
                    {
                        readBuffer[i] = readBuffer[i+mark_pos];
                    }
                    in_pos -= mark_pos;
                    mark_pos = 0;
                    in_size = in.read( readBuffer, in_pos, (IN_BUFSIZ - in_pos) );
                }
                else
                {
                    in_size = in.read( readBuffer, 0 , IN_BUFSIZ );
                    in_pos = 0;
                }

                if ( in_size < 0 )
                {
                    return (count == 0) ? -1 : count;
                }
                in_size += in_pos;
            }

            while ( in_pos < in_size && count < num )
            {
                buffer[offset++] = readBuffer[in_pos++];
                count++;
            }

            return count;
        }
        else
        {
            return in.read( buffer, offset, num );
        }
    }


    private void reset( ) throws IOException
    {
        if ( USE_INTERNAL_READ )
        {
            in_pos = mark_pos;
            in_mark = false;
        }
        else
        {
            in.reset();
        }
    }

    private void mark( int readLimit )
    {
        if ( USE_INTERNAL_READ )
        {
            in_mark = true;
            mark_pos = in_pos;
        }
        else
        {
            in.mark( readLimit );
        }
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

        this.mark(how_far);
        num_read = this.read(buffer, pos, how_far);
        if ( num_read >= 0 )
        {
            this.reset();
        }

        return num_read;
    }

    private HtmlToken getWhitespace() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 1;

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
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.WHITESPACE, charset );
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.WHITESPACE, charset );
    }

    private void advanceString(byte quote) throws IOException
    {
        int ch;
        int buf_end = BUFSIZ - 1;

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
        int buf_end = BUFSIZ - 1;

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
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.TAG_NAME, charset );
            }
            else if ( buffer[pos] == '/' || buffer[pos] == '>' )
            {
                state = State.IN_TEXT;
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.TAG_NAME, charset );
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.TAG_NAME, charset );
    }

    private HtmlToken getAttributeName() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 1;

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
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.ATTRIBUTE_NAME, charset );
            }
            else if ( buffer[pos] == '>' )
            {
                state = State.IN_TEXT;
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.ATTRIBUTE_NAME, charset );
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.ATTRIBUTE_NAME, charset );
    }

    private HtmlToken getAttributeValue() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 1;

        num_read = peek(1);
        if ( num_read < 0 )
        {
            state = State.EOF;
            return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.UNKNOWN, charset );
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
            return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.ATTRIBUTE_VALUE, charset );
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
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.ATTRIBUTE_VALUE, charset );
            }
            else if ( buffer[pos] == '>' )
            {
                state = State.IN_TEXT;
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.ATTRIBUTE_VALUE, charset );
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.ATTRIBUTE_VALUE, charset );
    }

    private HtmlToken getComment() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 3;

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( buffer[pos] == '-' )
            {
                // see if we have the end of comment
                num_read = peek(3);
                if ( num_read == 3 && buffer[pos+1] == '-' && buffer[pos+2] == '>' )
                {
                    state = State.IN_COMMENT_END;
                    return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.COMMENT, charset );
                }
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.COMMENT, charset );
    }

    private HtmlToken getCdata() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 3;

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( buffer[pos] == ']' )
            {
                // see if we have the end of comment
                num_read = peek(3);
                if ( num_read == 3 && buffer[pos+1] == ']' && buffer[pos+2] == '>' )
                {
                    state = State.IN_CDATA_END;
                    return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.CDATA, charset );
                }
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.CDATA, charset );
    }

    private HtmlToken getProcessingInstruction() throws IOException
    {
        int ch;
        int buf_end = BUFSIZ - 1;

        while ( pos < buf_end )
        {
            ch = this.read();
            if ( ch < 0 )
            {
                state = State.EOF;
                break;
            }
            buffer[pos++] = (byte)ch;

            if ( ch == '>' )
            {
                state = State.IN_TEXT;
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.PROCESSING_INSTRUCTION, charset );
            }
        }

        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.PROCESSING_INSTRUCTION, charset );
    }

    private HtmlToken getDocType() throws IOException
    {
        int ch;
        int buf_end = BUFSIZ - 1;

        while ( pos < buf_end )
        {
            ch = this.read();
            if ( ch < 0 )
            {
                state = State.EOF;
                break;
            }
            buffer[pos++] = (byte)ch;

            if ( ch == '>' )
            {
                state = State.IN_TEXT;
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.DOCTYPE, charset );
            }
        }

        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.DOCTYPE, charset );
    }

    private HtmlToken getText() throws IOException
    {
        int num_read;
        int buf_end = BUFSIZ - 1;

        while ( pos < buf_end )
        {
            num_read = peek(1);
            if ( num_read < 0 )
            {
                state = State.EOF;
                break;
            }

            if ( buffer[pos] == '<' )
            {
                return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.TEXT, charset );
            }
            buffer[pos++] = (byte)this.read();
        }

        return HtmlToken.getToken( Arrays.copyOfRange(buffer, 0, pos), 0, pos, HtmlToken.Type.TEXT, charset );
    }
}
