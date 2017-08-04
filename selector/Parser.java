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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoupstream.HtmlToken;


/**
 * Parser for a subset of CSS selectors
 * 
 */
public class Parser
{
    private static final Pattern START = Pattern.compile("start\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COUNT = Pattern.compile("count\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NTH_CHILD_ODD_EVEN = Pattern.compile("nth-child\\(([a-z]+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NTH_CHILD_AB = Pattern.compile("nth-child\\(((\\+|-)?(\\d+)?)n((\\+|-)?\\d+)?\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NTH_CHILD_B  = Pattern.compile("nth-child\\(((\\+|-)?(\\d+))\\)", Pattern.CASE_INSENSITIVE);
    Lexer lex;

    public Parser(InputStream input)
    {
        this.lex = new Lexer( input );
    }

    public Parser(String input)
    {
        ByteArrayInputStream in = new ByteArrayInputStream( input.getBytes(StandardCharsets.UTF_8) );
        this.lex = new Lexer( in );
    }

    public Parser(String input, Charset charset)
    {
        ByteArrayInputStream in = new ByteArrayInputStream( input.getBytes( charset ) );
        this.lex = new Lexer( in );
    }

    // TODO: Build the list of selectors
    // public List<Selector> parse() throws IOException, ParseException
    public List<Selector> parse() throws IOException, ParseException
    {
        int selector_count = 0;
        ArrayList<Selector> selectors = new ArrayList<Selector>();
        Selector current_selector = null;
        Component current_component = null;
        AttributeSelector current_attribute_selector = null;
        Action current_action = null;
        Component.CombinatorType combinator = Component.CombinatorType.ROOT;
        Token tok = lex.advance();
        Token.Type last_type = Token.Type.END_SELECTOR;
        String text;
        boolean need_tag = false;

        // reade through CSS tokens until end of file
        while ( tok.type != Token.Type.EOF )
        {
            text = new String( tok.text );

            if (last_type == Token.Type.END_SELECTOR || last_type == Token.Type.COMBINATOR) 
            {
                need_tag = true;
            }
            else
            {
                need_tag = false;
            }

            switch( tok.type )
            {
            case TAG:
                if ( current_attribute_selector != null)
                {
                    current_component.addAttribute( current_attribute_selector );
                }

                if ( need_tag )
                {
                    if ( combinator == Component.CombinatorType.ROOT )
                    {
                        selector_count++;
                        current_selector = new Selector( selector_count );
                    }
                    current_component = current_selector.addComponent( text, combinator );
                }
                break;
            case ATTRIBUTE_NAME:
                if (need_tag)
                {
                    if ( combinator == Component.CombinatorType.ROOT )
                    {
                        selector_count++;
                        current_selector = new Selector( selector_count );
                    }
                    current_component = current_selector.addComponent( "*", combinator );
                }
                if ( current_attribute_selector != null)
                {
                    current_component.addAttribute( current_attribute_selector );
                }
                current_attribute_selector = new AttributeSelector( text );
                break;
            case ATTRIBUTE_COMPARATOR:
                current_attribute_selector.setComparatorType( text );
                break;
            case ATTRIBUTE_VALUE:
                current_attribute_selector.setAttributeValue( text );
                current_component.addAttribute( current_attribute_selector );
                current_attribute_selector = null;
                break;
            case PSEUDO:
                if ( current_attribute_selector != null)
                {
                    current_component.addAttribute( current_attribute_selector );
                }

                if (need_tag)
                {
                    if ( combinator == Component.CombinatorType.ROOT )
                    {
                        selector_count++;
                        current_selector = new Selector( selector_count );
                    }
                    current_component = current_selector.addComponent( "*", combinator );
                }
                int a, b;
                if ( text.toLowerCase().equalsIgnoreCase( "before" ) )
                {
                    if ( current_selector.isAfter() )
                    {
                        throw new ParseException("Parse failed on line: "+lex.getLine()+" Cannot combine both :before and :after pseudo selectors");
                    }
                    current_selector.setBefore( true );
                }
                else if ( text.toLowerCase().equalsIgnoreCase( "after" ) )
                {
                    if ( current_selector.isBefore() )
                    {
                        throw new ParseException("Parse failed on line: "+lex.getLine()+" Cannot combine both :before and :after pseudo selectors");
                    }
                    current_selector.setAfter( true );
                }
                else if ( text.toLowerCase().startsWith( "start(" ) )
                {
                    Matcher matcher_start = START.matcher(text);
                    if ( matcher_start.matches() )
                    {
                        int start = Integer.parseInt(matcher_start.group(1));
                        current_selector.setStart( start );
                    }
                }
                else if ( text.toLowerCase().startsWith( "count(" ) )
                {
                    Matcher matcher_count = COUNT.matcher(text);
                    if ( matcher_count.matches() )
                    {
                        int count = Integer.parseInt(matcher_count.group(1));
                        current_selector.setCount( count );
                    }
                }
                else if ( text.equalsIgnoreCase( "first-child" ) )
                {
                    a=0;
                    b=1;

                    current_component.setNthChild( a, b );
                }
                else if ( text.toLowerCase().startsWith( "nth-child(" ) )
                {
                    Matcher matcher_start = START.matcher(text);
                    Matcher matcher_count = COUNT.matcher(text);
                    Matcher matcher_odd_even = NTH_CHILD_ODD_EVEN.matcher(text);
                    Matcher matcher_ab = NTH_CHILD_AB.matcher(text);
                    Matcher matcher_b = NTH_CHILD_B.matcher(text);
                    if ( matcher_odd_even.matches() )
                    {
                        String arg = matcher_odd_even.group(1);
                        if ( arg.equalsIgnoreCase( "odd" ) )
                        {
                            a = 2;
                            b = 1;
                        }
                        else if ( arg.equalsIgnoreCase( "even" ) )
                        {
                            a = 2;
                            b = 0;
                        }
                        else
                        {
                            throw new ParseException("Parse failed on line: "+lex.getLine()+" Could not parse nth-child '"+arg+"': unexpected argument");
                        }
                    }
                    else if ( matcher_ab.matches() )
                    {
                        a = matcher_ab.group(3) != null ? Integer.parseInt(matcher_ab.group(1).replaceFirst("^\\+", "")) : 1;
                        b = matcher_ab.group(4) != null ? Integer.parseInt(matcher_ab.group(4).replaceFirst("^\\+", "")) : 0;
                    }
                    else if ( matcher_b.matches() )
                    {
                        a = 0;
                        b = Integer.parseInt(matcher_b.group(1).replaceFirst("^\\+", ""));
                    }
                    else
                    {
                        throw new ParseException("Parse failed on line: "+lex.getLine()+" Could not parse nth-index '"+text+"': unexpected format");
                    }

                    current_component.setNthChild( a, b );
                }
                else
                {
                    throw new ParseException("Parse failed on line: "+lex.getLine()+" Could not parse '"+text+"': unsupported pseudo");
                }
                break;
            case COMBINATOR:
                switch ( tok.text[0] )
                {
                case '>':
                    combinator = Component.CombinatorType.CHILD;
                    break;
                case '+':
                    combinator = Component.CombinatorType.ADJACENT;
                    break;
                case '~':
                    combinator = Component.CombinatorType.SIBLING;
                    break;
                case ' ':
                default:
                    combinator = Component.CombinatorType.DESCENDENT;
                    break;
                }
                break;
            case CLASS:
                if ( current_attribute_selector != null)
                {
                    current_component.addAttribute( current_attribute_selector );
                }

                if (need_tag)
                {
                    if ( combinator == Component.CombinatorType.ROOT )
                    {
                        selector_count++;
                        current_selector = new Selector( selector_count );
                    }
                    current_component = current_selector.addComponent( "*", combinator );
                }
                current_attribute_selector = new AttributeSelector( "class" );
                current_attribute_selector.setComparatorType( "~=" );
                current_attribute_selector.setAttributeValue( text );
                current_component.addAttribute( current_attribute_selector );
                current_attribute_selector = null;
                break;
            case ID:
                if ( current_attribute_selector != null)
                {
                    current_component.addAttribute( current_attribute_selector );
                }

                if (need_tag)
                {
                    if ( combinator == Component.CombinatorType.ROOT )
                    {
                        selector_count++;
                        current_selector = new Selector( selector_count );
                    }
                    current_component = current_selector.addComponent( "*", combinator );
                }
                current_attribute_selector = new AttributeSelector( "id" );
                current_attribute_selector.setComparatorType( "=" );
                current_attribute_selector.setAttributeValue( text );
                current_component.addAttribute( current_attribute_selector );
                current_attribute_selector = null;
                break;
            case ACTION:
                if ( current_action != null)
                {
                    current_selector.addAction( current_action );
                    current_action = null;
                }
                try
                {
                    current_action = new Action( text );
                }
                catch ( ClassNotFoundException e )
                {
                    throw new ParseException("Parse failed on line: "+lex.getLine()+" could not find function: "+text);
                }
                break;
            case ACTION_ARGUMENT:
                current_action.addArgument( text );
                break;
            case END_SELECTOR:
                if ( current_action != null)
                {
                    current_selector.addAction( current_action );
                    current_action = null;
                }
                selectors.add( current_selector );
                current_selector = null;
                combinator = Component.CombinatorType.ROOT;
                break;
            default:
                throw new ParseException("Parse failed on line: "+lex.getLine()+" at or near: "+tok);
            }

            last_type = tok.type;

            tok = lex.advance();
        }

        if ( current_selector != null && current_action != null)
        {
            current_selector.addAction( current_action );
        }

        if ( current_selector != null )
        {
            selectors.add( current_selector );
        }

        return selectors;
    }
}
