// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.cscore.HttpCamera;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.math.trajectory.TrajectoryConfig;
import edu.wpi.first.math.trajectory.TrajectoryGenerator;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj.XboxController;
import edu.wpi.first.wpilibj.XboxController.Button;
import edu.wpi.first.wpilibj.shuffleboard.BuiltInWidgets;
import edu.wpi.first.wpilibj.shuffleboard.Shuffleboard;
import edu.wpi.first.wpilibj.shuffleboard.ShuffleboardTab;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import frc.commands.DefaultDriveCommand;
import frc.commands.DefaultTestMotorCommand;
import frc.robot.Constants.DriveConstants;
import frc.robot.Constants.OIConstants;
import frc.robot.Constants.TestMotorConstants;
import frc.robot.subsystems.DriveSubsystem;
import frc.robot.subsystems.TestMotorSubsystem;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.SwerveControllerCommand;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;
import edu.wpi.first.wpilibj2.command.button.Trigger;

import java.util.List;

public class RobotContainer {

  private final DriveSubsystem robotDrive = new DriveSubsystem();

  private final TestMotorSubsystem testMotor = new TestMotorSubsystem();

  XboxController driverController = new XboxController(OIConstants.kDriverControllerPort);

  XboxController subController = new XboxController(OIConstants.kCoPilotControllerPort);

  public RobotContainer() {
    // Configure the trigger bindings
    configureBindings();

    setUpShuffleBoard();

    robotDrive.setDefaultCommand(new DefaultDriveCommand(robotDrive));
    testMotor.setDefaultCommand(new DefaultTestMotorCommand(testMotor, TestMotorConstants.kTestMotorSpeedMedium));
  }

  private void configureBindings() {
    new JoystickButton(driverController, Button.kX.value).whileTrue(new RunCommand(
        () -> robotDrive.setX(), robotDrive));

    new JoystickButton(driverController, Button.kY.value).onTrue(new InstantCommand(
        () -> robotDrive.zeroHeading(), robotDrive));
    
    new JoystickButton(subController, Button.kX.value).whileTrue(new RunCommand(() -> testMotor.setRotationalMotorDutyCycle(1)));
    new JoystickButton(subController, Button.kB.value).whileTrue(new RunCommand(() -> testMotor.setRotationalMotorDutyCycle(-1)));

  }

  public Command getAutonomousCommand() {

    SendableChooser<AutoType> type = (SendableChooser<AutoType>)SmartDashboard.getData("Auto Type");
    double delay = SmartDashboard.getNumber("Auto Delay", 0);

    robotDrive.zeroHeading();

    
    // Create config for trajectory; basically setting for the trajectory
    TrajectoryConfig config = new TrajectoryConfig(DriveConstants.kMaxSpeedMetersPerSec,
        AutoConstants.kMaxAccelerationMetersPerSecondSquared)
        // Add kinematics to ensure max speed is actually obeyed
        .setKinematics(DriveConstants.kDriveKinematics);

    // An example trajectory to follow. All units in meters.
    /*
     * Trajectory exampleTrajectory = TrajectoryGenerator.generateTrajectory(
     * // Start at the origin facing the +X direction
     * new Pose2d(0, 0, new Rotation2d(0)),
     * // Pass through these two interior waypoints, making an 's' curve path
     * List.of(new Translation2d(1, 1), new Translation2d(2, -1)),
     * // End 3 meters straight ahead of where we started, facing forward
     * new Pose2d(3, 0, new Rotation2d(0)),
     * config);
     */

    // generate the trajectory
    Trajectory exampleTrajectory = TrajectoryGenerator.generateTrajectory(
        List.of( // trajectory motion position
            new Pose2d(new Translation2d(0, 0), Rotation2d.fromDegrees(0)), // final trajectory position
            new Pose2d(new Translation2d(1, 0), Rotation2d.fromDegrees(45)),
            new Pose2d(new Translation2d(1, -1), Rotation2d.fromDegrees(135)),
            new Pose2d(new Translation2d(2, -1), Rotation2d.fromDegrees(180))),
        config); // apply the previously written settings

    // PID Controllers for tracking trjectory
    PIDController xController = new PIDController(AutoConstants.kPXController, 0, 0);
    PIDController yController = new PIDController(AutoConstants.kPYController, 0, 0);
    ProfiledPIDController thetaController = new ProfiledPIDController( // PID Controller for theta
        AutoConstants.kPThetaController, 0, 0, AutoConstants.kThetaControllerConstraints);
    thetaController.enableContinuousInput(-Math.PI, Math.PI); // keep the PIDController continuous

    SwerveControllerCommand swerveControllerCommand = new SwerveControllerCommand(
        exampleTrajectory,
        robotDrive::getP, // Functional interface to feed supplier
        DriveConstants.kDriveKinematics,
        xController,
        yController,
        thetaController,
        robotDrive::setModuleStates,
        robotDrive);

    // Reset odometry to the starting pose of the trajectory.
    robotDrive.resetOdometry(exampleTrajectory.getInitialPose());

    // Run path following command, then stop at the end.
    return swerveControllerCommand.andThen(() -> robotDrive.drive(0, 0, 0, false, false));
  }

  public static final class AutoConstants { // constants for autonomous command by Felix
    public static final double kMaxSpeedMetersPerSecond = 3;
    public static final double kMaxAccelerationMetersPerSecondSquared = 3;
    public static final double kMaxAngularSpeedRadiansPerSecond = Math.PI;
    public static final double kMaxAngularSpeedRadiansPerSecondSquared = Math.PI;

    public static final double kPXController = 1;
    public static final double kPYController = 1;
    public static final double kPThetaController = 1;

    // Constraint for the motion profiled robot angle controller
    public static final TrapezoidProfile.Constraints kThetaControllerConstraints = new TrapezoidProfile.Constraints(
        kMaxAngularSpeedRadiansPerSecond, kMaxAngularSpeedRadiansPerSecondSquared);
  }

  public enum AutoType {
    
  }

  public void setUpShuffleBoard() {
    // Create a camera stream for Limelight
    HttpCamera limelightFeed = new HttpCamera("Limelight", "http://10.1.23.23:5800/stream.mjpg");
    
    // Add it to Shuffleboard
    Shuffleboard.getTab("Vision").add("Limelight Camera", limelightFeed);

    // Add Limelight targeting information to Shuffleboard
    ShuffleboardTab visionTab = Shuffleboard.getTab("Vision");

    visionTab.add("Target X", 0.0).withWidget(BuiltInWidgets.kTextView)
        .getEntry().setDouble(LimelightHelpers.getTX("limelight"));
    visionTab.add("Target Y", 0.0).withWidget(BuiltInWidgets.kTextView)
        .getEntry().setDouble(LimelightHelpers.getTY("limelight"));
    visionTab.add("Target Area", 0.0).withWidget(BuiltInWidgets.kTextView)
        .getEntry().setDouble(LimelightHelpers.getTA("limelight"));
}

  private boolean leftTrigger(){
    return (subController.getRawAxis(2) > 0.75);
  }
}
