package org.jsoupstream;

import java.util.List;
import java.util.ListIterator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import org.jsoupstream.selector.Selector;

/**
   Set of generic callback functions to call as CSS actions. 
    Note: Callback functions should return true unless you want to subsequent callback functions within the
    matched selector. Here is an example:

    h1 { callback_that_can_return_false(); callback_wont_be_called_when_previous_returns_false(); }
    h1 { callback_will_always_be_called_regardless_of_other_selectors_return(); }

 */
public class Functions
{
    private static HashMap<String, Pattern> patterns = new HashMap<String, Pattern>();

    public Functions() {}

    public boolean delete(Selector selector, List<HtmlToken> tokenQueue)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            lit.remove();
            HtmlToken.relinquish( token );
        }

        return true;
    }

    public boolean print(Selector selector, List<HtmlToken> tokenQueue)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        System.out.print( "[[" );
        while ( lit.hasNext() )
        {
            HtmlToken token = lit.next();
            System.out.print( token.str );
        }
        System.out.println( "]]" );

        return true;
    }

    public boolean printErr(Selector selector, List<HtmlToken> tokenQueue)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        System.err.print( "[[" );
        while ( lit.hasNext() )
        {
            HtmlToken token = lit.next();
            System.err.print( token.str );
        }
        System.err.println( "]]" );

        return true;
    }

    public boolean replaceAttribute(Selector selector, List<HtmlToken> tokenQueue, String attr, String newAttr)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        while ( lit.hasNext() )
        {
            HtmlToken token = lit.next();
            if ( token.type == HtmlToken.Type.ATTRIBUTE_NAME )
            {
                if ( attr.equalsIgnoreCase( token.str ) )
                {
                    lit.set( HtmlToken.getToken( newAttr.getBytes(), HtmlToken.Type.ATTRIBUTE_NAME ) );
                    HtmlToken.relinquish( token );
                    return true;
                }
            }
            else if ( token.type == HtmlToken.Type.CLOSE_TAG )
            {
                break;
            }
        }

        return true;
    }

    public boolean addAttribute(Selector selector, List<HtmlToken> tokenQueue, String name, String value)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.TAG_NAME )
            {
                lit.add( HtmlToken.getToken( " ".getBytes(), HtmlToken.Type.WHITESPACE ) );
                lit.add( HtmlToken.getToken( name.getBytes(), HtmlToken.Type.ATTRIBUTE_NAME ) );
                lit.add( HtmlToken.getToken( "=".getBytes(), HtmlToken.Type.EQUALS ) );
                lit.add( HtmlToken.getToken( ("\""+value+"\"").getBytes(), HtmlToken.Type.ATTRIBUTE_VALUE ) );
                break;
            }
            else if ( token.type == HtmlToken.Type.CLOSE_TAG )
            {
                break;
            }
        }

        return true;
    }

    public boolean addAttributeValue(Selector selector, List<HtmlToken> tokenQueue, String attr, String addValue)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;
        boolean found = false;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.ATTRIBUTE_NAME )
            {
                if ( attr.equalsIgnoreCase( token.str ) )
                {
                    found = true;
                }
            }
            else if ( token.type == HtmlToken.Type.CLOSE_TAG )
            {
                break;
            }
            else if ( found )
            {
                if ( token.type == HtmlToken.Type.ATTRIBUTE_VALUE )
                {
                    StringBuffer newValue = new StringBuffer( );
                    String value = token.str;
                    char quote = value.charAt( 0 );
                    String arr[] = value.substring( 1, value.length() - 1 ).split( " +" );
                    boolean first = true;
    
                    newValue.append( quote );
                    for ( String val : arr )
                    {
                        if ( ! val.equals( addValue ) )
                        {
                            if ( !first )
                            {
                                newValue.append( " " );
                            }
                            first = false;

                            newValue.append( val );
                        }
                    }
                    newValue.append( " " );
                    newValue.append( addValue );
                    newValue.append( quote );
                    lit.set( HtmlToken.getToken( newValue.toString().getBytes(), HtmlToken.Type.ATTRIBUTE_VALUE ) );
                    HtmlToken.relinquish( token );
                    return true;
                }
                else if ( token.type != HtmlToken.Type.WHITESPACE && token.type != HtmlToken.Type.EQUALS )
                {
                    found = false;
                }
            }
        }

        return true;
    }

    public boolean replace(Selector selector, List<HtmlToken> tokenQueue, String text)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;
        boolean replaced = false;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( ! replaced )
            {
                lit.set( HtmlToken.getToken( text.getBytes(), HtmlToken.Type.TEXT ) );
                replaced = true;
            }
            else
            {
                lit.remove();
            }
            HtmlToken.relinquish( token );
        }

        return true;
    }

    public boolean replaceText(Selector selector, List<HtmlToken> tokenQueue, String text)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        boolean replaced = false;
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.TEXT )
            {
                if ( ! replaced )
                {
                    lit.set( HtmlToken.getToken( text.getBytes(), HtmlToken.Type.TEXT ) );
                    replaced = true;
                }
                else
                {
                    lit.remove();
                }
                HtmlToken.relinquish( token );
            }
        }

        return true;
    }

    public boolean replaceExpression(Selector selector, List<HtmlToken> tokenQueue, String pattern, String text)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;

        Pattern compiledPattern = patterns.get( pattern );
        if ( compiledPattern == null )
        {
            compiledPattern = Pattern.compile( pattern );
            patterns.put( pattern, compiledPattern );
        }

        Matcher matcher;
        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.TEXT )
            {
                matcher = compiledPattern.matcher( token.str );
                lit.set( HtmlToken.getToken( (matcher.replaceAll( text )).getBytes(), HtmlToken.Type.TEXT ) );
                HtmlToken.relinquish( token );
            }
        }

        return true;
    }

    public boolean replaceInner(Selector selector, List<HtmlToken> tokenQueue, String text)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        boolean replaced = false;
        boolean started = false;
        int count = 0;
        int size = 0;
        HtmlToken token;

        size = tokenQueue.size() - 3;
        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( started && count < size )
            {
                if ( ! replaced )
                {
                    lit.set( HtmlToken.getToken( text.getBytes(), HtmlToken.Type.TEXT ) );
                    replaced = true;
                }
                else
                {
                    lit.remove();
                }
                HtmlToken.relinquish( token );
            }
            count++;
            if ( token.type == HtmlToken.Type.CLOSE_TAG )
            {
                started = true;
            }
        }

        return true;
    }

    public boolean wrapElement(Selector selector, List<HtmlToken> tokenQueue, String tag)
    {
        tokenQueue.add( 0, HtmlToken.getToken( ">".getBytes(), HtmlToken.Type.CLOSE_TAG ) );
        tokenQueue.add( 0, HtmlToken.getToken( tag.getBytes(), HtmlToken.Type.TAG_NAME ) );
        tokenQueue.add( 0, HtmlToken.getToken( "<".getBytes(), HtmlToken.Type.OPEN_TAG ) );
        tokenQueue.add( HtmlToken.getToken( "</".getBytes(), HtmlToken.Type.OPEN_END_TAG ) );
        tokenQueue.add( HtmlToken.getToken( tag.getBytes(), HtmlToken.Type.TAG_NAME ) );
        tokenQueue.add( HtmlToken.getToken( ">".getBytes(), HtmlToken.Type.CLOSE_TAG ) );

        return true;
    }

    public boolean insertBefore(Selector selector, List<HtmlToken> tokenQueue, String text)
    {
        HtmlToken token = HtmlToken.getToken( text.getBytes(), HtmlToken.Type.TEXT );
        tokenQueue.add( 0 , token );

        return true;
    }

    public boolean insertAfter(Selector selector, List<HtmlToken> tokenQueue, String text)
    {
        HtmlToken token = HtmlToken.getToken( text.getBytes(), HtmlToken.Type.TEXT );
        tokenQueue.add( token );

        return true;
    }

    public boolean contains(Selector selector, List<HtmlToken> tokenQueue, String value)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.TEXT )
            {
                if ( token.str.contains( value ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean notContains(Selector selector, List<HtmlToken> tokenQueue, String value)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.TEXT )
            {
                if ( token.str.contains( value ) )
                {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean containsIgnoreCase(Selector selector, List<HtmlToken> tokenQueue, String value)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.TEXT )
            {
                if ( token.str.toLowerCase().contains( value.toLowerCase() ) )
                {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean notContainsIgnoreCase(Selector selector, List<HtmlToken> tokenQueue, String value)
    {
        ListIterator<HtmlToken> lit = tokenQueue.listIterator();
        HtmlToken token;

        while ( lit.hasNext() )
        {
            token = lit.next();
            if ( token.type == HtmlToken.Type.TEXT )
            {
                if ( token.str.toLowerCase().contains( value.toLowerCase() ) )
                {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean done(Selector selector, List<HtmlToken> tokenQueue)
    {
        selector.setDone( true );
        return true;
    }
}
