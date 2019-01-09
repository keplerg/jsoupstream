package org.jsoupstream.selector;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.lang.StringBuffer;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.io.OutputStream;
import org.jsoupstream.HtmlToken;

/**
   Represents a single action (function call) with arguments that will get called when the associated selector matches.
 */
public class Action
{
    private Class<?> callbackClass = null;
    private static HashMap<String,Class<?>> callbackClasses = new HashMap<String,Class<?>>();
    private final String function;
    private ArrayList<String> arguments = new ArrayList<String>();

    public Action(String func) throws ClassNotFoundException
    {
        int pos = func.lastIndexOf( '.' );
        if ( pos < 0 )
        {
            this.callbackClass = callbackClasses.get( "org.jsoupstream.Functions" );
            if ( this.callbackClass == null )
            {
                this.callbackClass = Class.forName( "org.jsoupstream.Functions" );
                callbackClasses.put( "org.jsoupstream.Functions", this.callbackClass );
            }
            this.function = func;
        }
        else
        {
            String callbackClass = func.substring( 0, pos );
            this.callbackClass = callbackClasses.get( callbackClass );
            if ( this.callbackClass == null )
            {
                this.callbackClass = Class.forName( callbackClass );
                callbackClasses.put( callbackClass, this.callbackClass );
            }
            this.function = func.substring( pos + 1 );
        }
    }

    public String getFunction()
    {
        return function;
    }

    public int getArgumentCount()
    {
        return arguments.size();
    }

    public void addArgument(String argument)
    {
        arguments.add( argument );
    }

    public String getArgument(int index)
    {
        if ( index < 0 || index >= arguments.size() )
        {
            return null;
        }
        else
        {
            return arguments.get(index);
        }
    }

    public List<String> getArguments()
    {
        return arguments;
    }

    public boolean execute( Selector selector, List<HtmlToken>token_list )
    {
        boolean ret = false;
        try
        {
            // passed String parameters
            Class<?>[] params = new Class<?>[(arguments.size() + 2)];
            Object[] args = new Object[(arguments.size() + 2)];
 
            // Token list is sent as first parameter, then remaining are Strings
            params[0] = Selector.class;
            args[0] = selector;
            params[1] = List.class;
            args[1] = token_list;
            for ( int i = 0; i < arguments.size(); i++ )
            {
                params[i + 2] = String.class;
                args[i + 2] = arguments.get( i );
            }

            Object obj = callbackClass.getDeclaredConstructor().newInstance();

            // call the method
            Method method = callbackClass.getDeclaredMethod( function, params );
            ret = (boolean)method.invoke(obj, args);
        }
        catch ( Exception e )
        {
            System.err.println( "Error executing "+function+": "+e.getMessage() );
            e.printStackTrace();
        }
   
        return ret;
    }
        
    public static void setCallbackClass( String className, Class<?> callbackClass )
    {
        Action.callbackClasses.put( className, callbackClass );
    }
        
    public String toString()
    {
        StringBuffer sb = new StringBuffer();

        sb.append( this.function );
        sb.append( "(" );
        if ( arguments != null )
        {
            Iterator<String> it = arguments.iterator();
            while( it.hasNext() )
            {
                sb.append( it.next() );
                if( it.hasNext() )
                {
                    sb.append(", ");
                }
            }
        }
        sb.append( ")" );
        return sb.toString();
    }
}
