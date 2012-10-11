package edu.vu.isis.ammo.annotations;

/**
 * Provide meta data about targets.
 * For example, which components are implemented using this class.
 * <p>
 * <code>
 * MetaData ( triggers = {"smoke","full"}, 
 *       conception = "1.6.2", 
 *       activate = "1.6.3", 
 *       expire = "unlimited", units = {""} ) 
 *       </code>
 *             
 */
public @interface MetaData {

    /** indicate the components using the target */
    String[] inCompoent();

    /**
     * Indicates when the test is relevant. The test is not required to pass at
     * this point but is made available for developers to work against. In the
     * case of test driven development this indicates when
     */
    String conception() default "0.0.0";

    /**
     * Indicates when the test should become active. The test is expected to
     * pass starting at this version.
     */
    String activate() default "0.0.0";
    
    /**
     * Indicates when the item being tested is no longer expected to run.
     * Generally the test would fail if run
     */
    String deprecate() default "9999.0.0";
    
    /**
     * Indicates when the test no longer be invoked.
     * Generally the test would fail if run
     */
    String expire() default "9999.0.0";
}
