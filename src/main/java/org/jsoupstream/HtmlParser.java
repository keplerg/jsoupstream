package org.jsoupstream;

import java.util.Collection;
import java.util.List;
import java.util.Deque;
import java.util.HashSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import org.jsoupstream.selector.Parser;
import org.jsoupstream.selector.Selector;
import org.jsoupstream.selector.ParseException;

/**
 * The class that parses the HTNL InputStream. The document is tokenized and buffered a muach as need to either test 
 * against active selectors, or in the case of a match, to execute the associated actions.
 * 
 * @author Kepler Gelotte, kepler@neighborwebmaster.com
 */
public class HtmlParser {

    private static final int BUFSIZ = 8192;

    private static enum State
    {
        NOT_IN_TAG,
        IN_START_TAG,
        IN_COMMENT,
        IN_CDATA,
        IN_SELF_CLOSING_TAG,
        IN_END_TAG
    }

    private static ArrayList<String> minimizeSkipTags = new ArrayList<String>();
    static {
        minimizeSkipTags.add( "pre" );
        minimizeSkipTags.add( "script" );
    }

    private List<Selector> selectors;
    private Charset charset;
    private boolean minimizeHtml = false;
    private boolean suppressMinimizeHtml = false;

    public HtmlParser( InputStream selectorCss ) throws ParseException, IOException
    {
        this.charset = StandardCharsets.UTF_8;

        // Parse the CSS here so we can reuse it over many HTML files.
        Parser parser = new Parser( selectorCss );
        this.selectors = parser.parse();
    }

    public HtmlParser( InputStream selectorCss, Charset charset ) throws ParseException, IOException
    {
        this.charset = charset;

        // Parse the CSS here so we can reuse it over many HTML files.
        Parser parser = new Parser( selectorCss );
        this.selectors = parser.parse();
    }

    public void setMinimizeHtml( boolean minimizeHtml )
    {
        this.minimizeHtml = minimizeHtml; 
    }

    public void setMinimizeSkipTags( Collection<String> skipTags )
    {
        HtmlParser.minimizeSkipTags.clear( ); 
        HtmlParser.minimizeSkipTags.addAll( skipTags ); 
    }

    public String parse ( HtmlLexer lexer ) throws IOException
    {
        State prevState = HtmlParser.State.NOT_IN_TAG;
        State state = HtmlParser.State.NOT_IN_TAG;
        StringBuffer outBuffer = new StringBuffer();
        Integer start;
        int currentLevel = 0;
        ArrayList<Deque<HtmlToken>> stack = new ArrayList<Deque<HtmlToken>>();
        Deque<HtmlToken> stackTokens;
        HtmlToken stackToken;
        ArrayList<HtmlToken> tokenBuffer = new ArrayList<HtmlToken>(100);
        ArrayDeque<Integer> bufferingStart = new ArrayDeque<Integer>();
        ArrayList<Selector> deferredExecute = new ArrayList<Selector>();
        HtmlToken token = lexer.advance();
        String currentTag = null;
        int endTagStart = 0;
        boolean passThru = false;
        boolean matchedCdata = false;
        int cdataSequence = 0;
        boolean matchedComment = false;
        int commentSequence = 0;
        List<HtmlToken> tokenQueue;

        lexer.setCharset( charset );

        while ( token.type != HtmlToken.Type.EOF )
        {
            if ( activeSelectorCount(deferredExecute) == 0 && ! minimizeHtml )
            {
                // all selectors have been satified - no more parsing required
                passThru = true;
                if ( tokenBuffer.size() > 0 )
                {
                    for ( HtmlToken tok : tokenBuffer )
                    {
                        outBuffer.append( tok.str );
                        HtmlToken.relinquish( tok );
                    }
                    tokenBuffer.clear();
                }
                outBuffer.append( token.str );
                HtmlToken.relinquish( token );
                byte[] buffer = new byte[BUFSIZ];
                ArrayDeque<Byte> unicode = new ArrayDeque<Byte>();
                int num = lexer.read( buffer, 0, BUFSIZ );
                int offset = 0;
                while ( num > 0 )
                {
                    // back off any partially read Unicode value
                    offset = 0;
                    if ( this.charset == StandardCharsets.UTF_8 )
                    {
                        while ( (buffer[num - offset - 1] & 0x80) == 0x80 )
                        {
                            offset++;
                            if ( (buffer[num - offset] & 0x40) == 0x40 )
                            {
                                if ( ( (buffer[num - offset] & 0xE0) == 0xC0 && offset == 2 )
                                    || ( (buffer[num - offset] & 0xF0) == 0xE0 && offset == 3 )
                                    || ( (buffer[num - offset] & 0xF8) == 0xF0 && offset == 4 ) )
                                {
                                    // full unicode value on buffer
                                    offset = 0;
                                }
                                break;
                            }
                        }

                        // store partially read Unicode value
                        while ( offset > 0 )
                        {
                            num--;
                            unicode.push( Byte.valueOf( buffer[num] ) );
                            buffer[num] = (byte)0;
                            offset--;
                        }
                    }

                    outBuffer.append( new String( buffer, 0, num, charset ) );
 
                    // restore partially read Unicode value to buffer
                    while ( unicode.size() > 0 )
                    {
                        buffer[offset++] = unicode.pop( );
                    }

                    num = lexer.read( buffer, offset, (BUFSIZ - offset) );
                    num += offset;
                }
                break;
            }

            if ( bufferingStart.size() == 0 )
            {
                if ( tokenBuffer.size() > 0 )
                {
                    for ( HtmlToken tok : tokenBuffer )
                    {
                        outBuffer.append( tok.str );
                        HtmlToken.relinquish( tok );
                    }
                    tokenBuffer.clear();
                }
                // always buffer at least the latest token
                tokenBuffer.add( token );
            }
            else
            {
                tokenBuffer.add( token );
            }

            switch ( token.type )
            {
            case WHITESPACE:
                if ( minimizeHtml && ! suppressMinimizeHtml )
                {
                    token.str = " ";
                }
                break;
            case TEXT:
                if ( minimizeHtml && ! suppressMinimizeHtml )
                {
                    String newTokenStr = token.str.replaceAll("\\s+", " ");
                    if ( newTokenStr.equals( " " ) )
                    {
                        token.str = "";
                    }
                    else
                    {
                        token.str = newTokenStr;
                    }
                }
                break;
            case OPEN_TAG:
                bufferingStart.push( Integer.valueOf( tokenBuffer.size() - 1 ) );
                state = HtmlParser.State.IN_START_TAG;
                break;
            case TAG_NAME:
                currentTag = token.str;
                if ( currentLevel >= stack.size() )
                {
                    stack.add( new ArrayDeque<HtmlToken>() );
                }
                stackTokens = stack.get( currentLevel );
                stackToken = ( stackTokens.size() == 0 ) ? null : stackTokens.peek( );

                if ( state == HtmlParser.State.IN_START_TAG )
                {
                    if ( minimizeHtml )
                    {
                        for ( String skipTag : minimizeSkipTags )
                        {
                            if ( currentTag.equalsIgnoreCase( skipTag ) )
                            {
                                suppressMinimizeHtml = true;
                            }
                        }
                    }
                    if ( stackToken != null && stackToken.symbol != null && stackToken.symbol.implied != null )
                    {
                        for ( String sym : stackToken.symbol.implied )
                        {
                            if ( currentTag.equalsIgnoreCase( sym ) )
                            {
                                // implied close of previous sibling element
                                if ( bufferingStart.size() > 1 )
                                {
                                    // remove start of current tag breifly while we handle last tag that wasn't closed
                                    Integer currentStart = bufferingStart.pop();
                                    start = bufferingStart.pop();
                                    tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() - 2 );
                                    HashSet<Selector> removeSet = new HashSet<Selector>();
                                    for ( Selector selector : deferredExecute )
                                    {
                                        if ( ! selector.isExpired() )
                                        {
                                            selector.executeActions( tokenQueue, removeSet, currentLevel, false );
                                        }
                                    }
                                    deferredExecute.removeAll( removeSet );
                                    bufferingStart.push( currentStart );
                                }
                                relinquishHtmlTokens( stack, currentLevel );
                                currentLevel--;
                                break;
                            }
                        }
                    }
                    currentLevel++;

                    // now add the tag to the stack at the correct level
                    if ( currentLevel >= stack.size() )
                    {
                        stack.add( new ArrayDeque<HtmlToken>() );
                    }
                    stackTokens = stack.get( currentLevel );
                    token.onStack = true;
                    stackTokens.push( token );
                }
                else if ( state == HtmlParser.State.IN_END_TAG )
                {
                    if ( minimizeHtml )
                    {
                        for ( String skipTag : minimizeSkipTags )
                        {
                            if ( currentTag.equalsIgnoreCase( skipTag ) )
                            {
                                suppressMinimizeHtml = false;
                            }
                        }
                    }
                    stackTokens = stack.get( currentLevel );
                    if ( stackTokens.size() > 0 )
                    {
                        // Make sure we match the start tag on the stack or there is an implied close
                        stackToken = stackTokens.peek( );
                        if ( stackToken.str.equalsIgnoreCase( "body" ) || stackToken.str.equalsIgnoreCase( "html" ) )
                        {
                            // implied close of previous sibling element
                            if ( bufferingStart.size() > 0 )
                            {
                                // remove start of current tag breifly while we handle last tag that wasn't closed
                                start = bufferingStart.pop();
                                tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() - 2 );
                                for ( Selector selector : deferredExecute )
                                {
                                    if ( ! selector.isExpired() )
                                    {
                                        selector.executeActions( tokenQueue, null, currentLevel, false );
                                    }
                                }
                                bufferingStart.push( tokenBuffer.size() - 2 );
                            }
                        }
                    }
                }
                break;
            case CLOSE_TAG:
                if ( state == HtmlParser.State.IN_START_TAG )
                {
                    start = bufferingStart.peek();
                    tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() );
                    stackTokens = stack.get( currentLevel );
                    stackToken = ( stackTokens.size() == 0 ) ? null : stackTokens.peek( );
                    boolean keepBuffering = false;

                    for ( Selector selector : selectors )
                    {
                        if ( ! selector.isExpired() )
                        {
                            if ( selector.check( stackToken, tokenQueue, currentLevel, stackTokens.size() ) )
                            {
                                if ( selector.isBuffering() && bufferingStart.size() > 0 )
                                {
                                    // is this a self closing tag?
                                    if ( stackToken != null && stackToken.getSymbolType() == SymbolTable.Type.VOID_ELEMENT )
                                    {
                                        selector.executeActions( tokenQueue, null, currentLevel, false );
                                    }
                                    else // normal open tag
                                    {
                                        deferredExecute.add( selector );
                                        keepBuffering = true;
                                    }
                                }
                                else if ( selector.isBefore() )
                                {
                                    selector.executeActions( tokenQueue.subList(0,0), null, currentLevel, false );
                                }
                                else if ( selector.isAfter() )
                                {
                                    // is this a self closing tag?
                                    if ( stackToken != null && stackToken.getSymbolType() == SymbolTable.Type.VOID_ELEMENT )
                                    {
                                        selector.executeActions( tokenQueue.subList(tokenQueue.size(),tokenQueue.size()), null, currentLevel, false );
                                    }
                                    else
                                    {
                                        deferredExecute.add( selector );
                                    }
                                }
                            }
                        }
                    }

                    if ( ! keepBuffering )
                    {
                        start = bufferingStart.pop();
                    }
                    if ( stackToken != null && stackToken.getSymbolType() == SymbolTable.Type.VOID_ELEMENT )
                    {
                        relinquishHtmlTokens( stack, currentLevel );
                        currentLevel--;
                    }
                }
                else if ( state == HtmlParser.State.IN_END_TAG )
                {
                    if ( checkOnStack( currentTag, stack, currentLevel, outBuffer ) )
                    {
                        while ( currentLevel > 0 )
                        {
                            // Make sure we match the start tag on the stack or there is a forced close
                            stackTokens = stack.get( currentLevel );
                            if ( stackTokens.size() > 0 )
                            {
                                stackToken = stackTokens.peek( );
                            }
                            else
                            {
                                stackToken = null;
                            }

                            if ( deferredExecute.size() > 0 )
                            {
                                boolean implied = false;
                                if ( bufferingStart.size() > 0 )
                                {
                                    start = bufferingStart.pop();
                                    if ( stackToken != null && currentTag.equalsIgnoreCase( stackToken.str ) )
                                    {
                                        tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() );
                                    }
                                    else
                                    {
                                        implied = true;
                                        tokenQueue = tokenBuffer.subList( start, (endTagStart - 1) );
                                    }
                                }
                                else
                                {
                                    tokenQueue = tokenBuffer.subList( tokenBuffer.size(), tokenBuffer.size() );
                                }
                                HashSet<Selector> removeSet = new HashSet<Selector>();
                                for ( Selector selector : deferredExecute )
                                {
                                    if ( ! selector.isExpired() )
                                    {
                                        selector.executeActions( tokenQueue, removeSet, currentLevel, implied );
                                    }
                                }
                                deferredExecute.removeAll( removeSet );
                            }

                            relinquishHtmlTokens( stack, currentLevel );
                            currentLevel--;

                            if ( currentTag.equalsIgnoreCase( stackToken.str ) )
                            {
                                break;
                            }
                        }
                    }
                    currentTag = null;
                }
                state = HtmlParser.State.NOT_IN_TAG;
                break;
            case END_SELF_CLOSING_TAG:
                if ( currentTag != null )
                {
                    if ( bufferingStart.size() > 0 )
                    {
                        stackTokens = stack.get( currentLevel );
                        stackToken = ( stackTokens.size() == 0 ) ? null : stackTokens.peek( );
                        // need to make sure the element is truly self closing
                        if ( stackToken != null && stackToken.getSymbolType() == SymbolTable.Type.VOID_ELEMENT )
                        {
                            relinquishHtmlTokens( stack, currentLevel );
                            currentLevel--;
                        }
                        start = bufferingStart.pop();
                        tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() );
                        for ( Selector selector : selectors )
                        {
                            if ( ! selector.isExpired() )
                            {
                                if ( selector.check( stackToken, tokenQueue, currentLevel, stackTokens.size() ) )
                                {
                                    if ( selector.isBuffering() )
                                    {
                                        if ( stackToken != null && stackToken.getSymbolType() == SymbolTable.Type.VOID_ELEMENT )
                                        {
                                            selector.executeActions( tokenQueue, null, currentLevel, false );
                                        }
                                        else
                                        {
                                            deferredExecute.add( selector );
                                        }
                                    }
                                    else if ( selector.isBefore() )
                                    {
                                        selector.executeActions( tokenQueue.subList(0,0), null, currentLevel, false );
                                    }
                                    else if ( selector.isAfter() )
                                    {
                                        selector.executeActions( tokenQueue.subList(tokenQueue.size(),tokenQueue.size()), null, currentLevel, false );
                                    }
                                }
                            }
                        }
                    }
                }
                currentTag = null;
                state = HtmlParser.State.NOT_IN_TAG;
                break;
            case OPEN_END_TAG:
                endTagStart = tokenBuffer.size();
                state = HtmlParser.State.IN_END_TAG;
                break;
            case DOCTYPE:
                currentLevel++;
                tokenQueue = tokenBuffer.subList( (tokenBuffer.size() - 1), tokenBuffer.size() );
                for ( Selector selector : selectors )
                {
                    if ( selector.check( null, tokenQueue, currentLevel, 0 ) )
                    {
                        if ( ! selector.isExpired() )
                        {
                            selector.executeActions( tokenQueue, null, currentLevel, false );
                        }
                    }
                }
                currentLevel--;
                break;
            case PROCESSING_INSTRUCTION:
                currentLevel++;
                tokenQueue = tokenBuffer.subList( (tokenBuffer.size() - 1), tokenBuffer.size() );
                for ( Selector selector : selectors )
                {
                    if ( selector.check( null, tokenQueue, currentLevel, 0 ) )
                    {
                        if ( ! selector.isExpired() )
                        {
                            selector.executeActions( tokenQueue, null, currentLevel, false );
                        }
                    }
                }
                currentLevel--;
                break;
            case START_CDATA:
                suppressMinimizeHtml = true;
                currentLevel++;
                if ( cdataSequence == 0 && currentLevel < stack.size() )
                {
                    stackTokens = stack.get( currentLevel );
                    cdataSequence = stackTokens.size();
                }
                cdataSequence++;
                tokenQueue = tokenBuffer.subList( (tokenBuffer.size() - 1), tokenBuffer.size() );
                for ( Selector selector : selectors )
                {
                    if ( selector.check( null, tokenQueue, currentLevel, cdataSequence ) )
                    {
                        matchedCdata = true;
                    }
                }
                if ( matchedCdata )
                {
                    bufferingStart.push( Integer.valueOf( tokenBuffer.size() - 1 ) );
                }
                else
                {
                    cdataSequence = 0;
                }
                prevState = state;
                state = HtmlParser.State.IN_CDATA;
                break;
            case CDATA:
                break;
            case END_CDATA:
                suppressMinimizeHtml = false;
                if ( matchedCdata && bufferingStart.size() > 0 )
                {
                    start = bufferingStart.pop();
                    tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() );
                    for ( Selector selector : selectors )
                    {
                        if ( ! selector.isExpired() )
                        {
                            selector.executeActions( tokenQueue, null, currentLevel, false );
                        }
                    }
                }
                currentLevel--;
                matchedCdata = false;
                state = prevState;
                break;
            case START_COMMENT:
                currentLevel++;
                if ( commentSequence == 0 && currentLevel < stack.size() )
                {
                    stackTokens = stack.get( currentLevel );
                    commentSequence = stackTokens.size();
                }
                commentSequence++;
                tokenQueue = tokenBuffer.subList( (tokenBuffer.size() - 1), tokenBuffer.size() );
                for ( Selector selector : selectors )
                {
                    if ( selector.check( null, tokenQueue, currentLevel, commentSequence ) )
                    {
                        matchedComment = true;
                    }
                }
                if ( matchedComment )
                {
                    bufferingStart.push( Integer.valueOf( tokenBuffer.size() - 1 ) );
                    suppressMinimizeHtml = true;
                }
                else
                {
                    if ( minimizeHtml )
                    {
                        tokenBuffer.remove( (tokenBuffer.size() - 1) );
                    }
                    commentSequence = 0;
                }
                prevState = state;
                state = HtmlParser.State.IN_COMMENT;
                break;
            case COMMENT:
                if ( minimizeHtml && ! suppressMinimizeHtml )
                {
                    tokenBuffer.remove( (tokenBuffer.size() - 1) );
                }
                break;
            case END_COMMENT:
                if ( matchedComment && bufferingStart.size() > 0 )
                {
                    start = bufferingStart.pop();
                    tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() );
                    for ( Selector selector : selectors )
                    {
                        if ( ! selector.isExpired() )
                        {
                            selector.executeActions( tokenQueue, null, currentLevel, false );
                        }
                    }
                }
                else if ( minimizeHtml && ! suppressMinimizeHtml )
                {
                    tokenBuffer.remove( (tokenBuffer.size() - 1) );
                }
                currentLevel--;
                matchedComment = false;
                suppressMinimizeHtml = false;
                state = prevState;
                break;
            default:
                break;
            }

            token = lexer.advance();
        }

        if ( ! passThru )
        {
            // implied at end of file
            while ( bufferingStart.size() > 0 )
            {
                tokenQueue = tokenBuffer.subList( bufferingStart.pop(), tokenBuffer.size() );
                for ( Selector selector : deferredExecute )
                {
                    if ( ! selector.isExpired() )
                    {
                        selector.executeActions( tokenQueue, null, currentLevel, false );
                    }
                }
            }

            if ( tokenBuffer.size() > 0 )
            {
                for ( HtmlToken tok : tokenBuffer )
                {
                    outBuffer.append( tok.str );
                    HtmlToken.relinquish( tok );
                }
            }
            HtmlToken.relinquish( token );
        }
        HtmlToken.replenish( );

        // outBuffer.append("\nCurrent level "+currentLevel+"\n");
        // outBuffer.append("\nbufferingStart size "+bufferingStart.size()+"\n");
        // outBuffer.append("\nPool size "+HtmlToken.getPoolSize()+"\n");

        return outBuffer.toString();
    }

    public void reset()
    {
        for ( Selector selector : selectors )
        {
            selector.reset();
        }
    }

    private void relinquishHtmlTokens( List<Deque<HtmlToken>> stack, int level )
    {
        Deque<HtmlToken> stackTokens;

        if ( level < 0 || stack == null || stack.size() == 0 )
        {
            return;
        }

        level++;
        while ( level < stack.size() )
        {
            stackTokens = stack.get( level );
            for ( HtmlToken token : stackTokens )
            {
                HtmlToken.forceRelinquish( token );
            }
            stackTokens.clear();
            level++;
        }
    }

    private int activeSelectorCount( List<Selector> deferredExecute )
    {
        int count = 0;

        for ( Selector selector : selectors )
        {
            if ( deferredExecute.contains( selector ) || ! selector.isExpired() )
            {
                count++;
            }
        }

        return count;
    }

    private boolean checkOnStack( String tag, List<Deque<HtmlToken>> stack, int level, StringBuffer sb )
    {
        Deque<HtmlToken> stackTokens;
        HtmlToken token;
        boolean ret = false;

        if ( tag == null || stack == null || stack.size() == 0 )
        {
            return false;
        }

        while ( level >= 0 )
        {
            stackTokens = stack.get( level );
            token = ( stackTokens.size() == 0 ) ? null : stackTokens.peek( );
            if ( token != null )
            {
                if ( tag.equalsIgnoreCase( token.str ) )
                {
                    ret = true;
                    break;
                }
            }
            level--;
        }

        return ret;
    }

    private void printStack( List<Deque<HtmlToken>> stack, int level, StringBuffer sb )
    {
        Deque<HtmlToken> stackTokens;
        HtmlToken token;
        boolean ret = false;

        sb.append( "\n[[STACK]] size: " + stack.size() + " currentLevel: " + level );
        if ( stack == null || stack.size() == 0 )
        {
            return;
        }

        for ( int i = 1 ; i < stack.size(); i++ )
        {
            stackTokens = stack.get( i );
            if ( stackTokens.size() > 0 )
            {
                Iterator<HtmlToken> it = stackTokens.iterator();
                sb.append( "\n"+i+" " );
                while ( it.hasNext() )
                {
                    token = it.next();
                    sb.append( "["+token.str+"]" );
                }
            }
            else
            {
                sb.append( "\n"+i+" [EMPTY]" );
            }
        }
        sb.append( "\n[[-----]]\n" );
    }

    private String printQueue( List<HtmlToken> queue )
    {
        StringBuffer sb = new StringBuffer();
        printQueue( queue, sb );
        return sb.toString();
    }

    private void printQueue( List<HtmlToken> queue, StringBuffer sb )
    {
        HtmlToken token;

        if ( queue == null || queue.size() == 0 )
        {
            return;
        }

        for ( int i = 1 ; i < queue.size(); i++ )
        {
            token = queue.get( i );
            if ( token != null )
            {
                sb.append( token.str );
            }
        }
    }
}
