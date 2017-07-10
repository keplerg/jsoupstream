package org.jsoupstream;

import java.util.List;
import java.util.Deque;
import java.util.HashSet;
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

    private static enum State
    {
        NOT_IN_TAG,
        IN_START_TAG,
        IN_SELF_CLOSING_TAG,
        IN_END_TAG
    }

    private List<Selector> selectors;
    private Charset charset;

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

    public String parse ( HtmlLexer lexer ) throws IOException
    {
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
        List<HtmlToken> tokenQueue;

        lexer.setCharset( charset );

        while ( token.type != HtmlToken.Type.EOF )
        {
            if ( activeSelectorCount(deferredExecute) == 0 )
            {
                passThru = true;
                if ( tokenBuffer.size() > 0 )
                {
                    for ( HtmlToken tok : tokenBuffer )
                    {
                        // out.write( tok.text );
                        outBuffer.append( tok.str );
                        HtmlToken.relinquish( tok );
                    }
                    tokenBuffer.clear();
                }
                outBuffer.append( token.str );
                HtmlToken.relinquish( token );
                byte[] buffer = new byte[4096];
                int num = lexer.read( buffer, 0, 4096 );
                while ( num > 0 )
                {
                    outBuffer.append( new String( buffer, 0, num, charset ) );
                    num = lexer.read( buffer, 0, 4096 );
                }
                break;
            }

            if ( token.type != HtmlToken.Type.OPEN_TAG && bufferingStart.size() == 0 )
            {
                if ( tokenBuffer.size() > 0 )
                {
                    for ( HtmlToken tok : tokenBuffer )
                    {
                        // out.write( tok.text );
                        outBuffer.append( tok.str );
                        HtmlToken.relinquish( tok );
                    }
                    tokenBuffer.clear();
                }
                // out.write( token.text );
                outBuffer.append( token.str );
                HtmlToken.relinquish( token );
            }
            else
            {
                tokenBuffer.add( token );
            }

            switch ( token.type )
            {
            case OPEN_TAG:
                bufferingStart.push( new Integer( tokenBuffer.size() - 1 ) );
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
                                    start = bufferingStart.pop();
                                    start = bufferingStart.peek();
                                    tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() - 2 );
                                    HashSet<Selector> removeSet = new HashSet<Selector>();
                                    for ( Selector selector : deferredExecute )
                                    {
                                        if ( ! selector.isExpired() )
                                        {
                                            if ( selector.executeActions( tokenQueue, removeSet, currentLevel, false ) )
                                            {
                                                start = bufferingStart.pop();
                                                break;
                                            }
                                        }
                                    }
                                    deferredExecute.removeAll( removeSet );
                                    bufferingStart.push( tokenBuffer.size() - 2 );
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
                    if ( bufferingStart.size() > 0 )
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
                                    break;
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
                }
                else if ( state == HtmlParser.State.IN_END_TAG )
                {
                    if ( checkOnStack( currentTag, stack, currentLevel, outBuffer ) )
                    {
                        while ( currentTag != null && currentLevel > 0 )
                        {
                            stackTokens = stack.get( currentLevel );
                            if ( stackTokens.size() > 0 )
                            {
                                // Make sure we match the start tag on the stack or there is a forced close
                                stackToken = stackTokens.peek( );
                                if ( bufferingStart.size() > 0 )
                                {
                                    start = bufferingStart.peek();
                                    boolean implied = false;
                                    if ( currentTag.equalsIgnoreCase( stackToken.str ) )
                                    {
                                        tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() );
                                    }
                                    else
                                    {
                                        implied = true;
                                        tokenQueue = tokenBuffer.subList( start, (endTagStart - 1) );
                                    }
                                    HashSet<Selector> removeSet = new HashSet<Selector>();
                                    for ( Selector selector : deferredExecute )
                                    {
                                        if ( ! selector.isExpired() )
                                        {
                                            if ( selector.executeActions( tokenQueue, removeSet, currentLevel, implied ) )
                                            {
                                                if ( bufferingStart.size() > 0 )
                                                {
                                                    start = bufferingStart.pop();
                                                }
                                            }
                                        }
                                    }
                                    deferredExecute.removeAll( removeSet );
                                }
                                if ( currentTag.equalsIgnoreCase( stackToken.str ) )
                                {
                                    currentTag = null;
                                }
                                else if ( stackToken.symbol != null && stackToken.symbol.implied != null )
                                {
                                    for ( String sym : stackToken.symbol.implied )
                                    {
                                        if ( currentTag.equalsIgnoreCase( sym ) )
                                        {
                                            currentTag = null;
                                            break;
                                        }
                                    }
                                }
    
                                relinquishHtmlTokens( stack, currentLevel );
                                currentLevel--;
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
                            start = bufferingStart.pop();
                            tokenQueue = tokenBuffer.subList( start, tokenBuffer.size() );
                            for ( Selector selector : selectors )
                            {
                                if ( ! selector.isExpired() )
                                {
                                    if ( selector.check( stackToken, tokenQueue, currentLevel, stackTokens.size() ) )
                                    {
                                        selector.executeActions( tokenQueue, null, currentLevel, false );
                                    }
                                }
                            }
                        }
                        else
                        {
                            tokenQueue = tokenBuffer.subList( bufferingStart.pop(), tokenBuffer.size() );
                            for ( Selector selector : selectors )
                            {
                                if ( selector.check( stackToken, tokenQueue, currentLevel, stackTokens.size() ) )
                                {
                                    deferredExecute.add( selector );
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
            case COMMENT:
                // System.out.println( "[["+token.str+"]]" );
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

        for ( int i = 0 ; i < stack.size(); i++ )
        {
            stackTokens = stack.get( i );
            token = ( stackTokens.size() == 0 ) ? null : stackTokens.peek( );
            if ( token != null )
            {
                sb.append( "\n"+i+" [["+token.str+"]]" );
            }
            else
            {
                sb.append( "\n"+i+" [[EMPTY]]" );
            }
        }
        sb.append( "\n[[-----]]\n" );
    }
}
