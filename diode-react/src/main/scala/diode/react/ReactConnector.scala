package diode.react

import diode._
import japgolly.scalajs.react._

import scala.language.existentials
import scala.scalajs.js

/**
  * Wraps a model reader, dispatcher and React connector to be passed to React components
  * in props.
  */
case class ModelProxy[S](modelReader: ModelR[_, S], dispatch: Action => Callback,
  connector: ReactConnector[_ <: AnyRef]) {
  def value = modelReader()


  def apply() = modelReader()

  def zoom[T](f: S => T)(implicit feq: FastEq[_ >: T]) = ModelProxy(modelReader.zoom(f), dispatch, connector)

  def wrap[T <: AnyRef, C](f: S => T)(compB: ModelProxy[T] => C)
    (implicit ev: C => ReactElement, feq: FastEq[_ >: T]): C = compB(zoom(f))

  def connect[T <: AnyRef](f: S => T)
    (implicit feq: FastEq[_ >: T]): ReactComponentC.ReqProps[ModelProxy[T] => ReactElement, T, _, TopNode] = {
    connector.connect(modelReader.zoom(f))
  }
}

trait ReactConnector[M <: AnyRef] {
  circuit: Circuit[M] =>

  /**
    * Wraps a React component by providing it an instance of ModelProxy for easy access to the model and dispatcher.
    *
    * @param zoomFunc Function to retrieve relevant piece from the model
    * @param compB    Function that creates the wrapped component
    * @return The component returned by `compB`
    */
  def wrap[S <: AnyRef, C](zoomFunc: M => S)(compB: ModelProxy[S] => C)
    (implicit ev: C => ReactElement, feq: FastEq[_ >: S]): C = {
    wrap(circuit.zoom(zoomFunc))(compB)
  }

  /**
    * Wraps a React component by providing it an instance of ModelProxy for easy access to the model and dispatcher.
    *
    * @param modelReader A reader that returns the piece of model we are interested in
    * @param compB       Function that creates the wrapped component
    * @return The component returned by `compB`
    */
  def wrap[S <: AnyRef, C](modelReader: ModelR[_, S])(compB: ModelProxy[S] => C)
    (implicit ev: C => ReactElement, feq: FastEq[_ >: S]): C = {
    compB(ModelProxy(modelReader, action => Callback(circuit.dispatch(action)), ReactConnector.this))
  }

  /**
    * Connects a React component into the Circuit by wrapping it in another component that listens to
    * relevant state changes and updates the wrapped component as needed.
    *
    * @param zoomFunc Function to retrieve relevant piece from the model
    * @param key      Optional parameter specifying a unique React key for this component.
    * @return A React component accepting a prop that is a function to creates the wrapped component
    */
  def connect[S <: AnyRef](zoomFunc: M => S, key: js.Any)
    (implicit feq: FastEq[_ >: S]): ReactComponentC.ReqProps[ModelProxy[S] => ReactElement, S, _, TopNode] = {
    connect(circuit.zoom(zoomFunc), key)
  }

  def connect[S <: AnyRef](zoomFunc: M => S)
    (implicit feq: FastEq[_ >: S]): ReactComponentC.ReqProps[ModelProxy[S] => ReactElement, S, _, TopNode] = {
    connect(circuit.zoom(zoomFunc))
  }

  /**
    * Connects a React component into the Circuit by wrapping it in another component that listens to
    * relevant state changes and updates the wrapped component as needed.
    *
    * @param modelReader A reader that returns the piece of model we are interested in
    * @param key         Optional parameter specifying a unique React key for this component.
    * @return A React component accepting a prop that is a function to create the wrapped component
    */
  def connect[S <: AnyRef](modelReader: ModelR[_, S], key: js.UndefOr[js.Any] = js.undefined)
    (implicit feq: FastEq[_ >: S]): ReactComponentC.ReqProps[ModelProxy[S] => ReactElement, S, _, TopNode] = {

    class Backend(t: BackendScope[ModelProxy[S] => ReactElement, S]) {
      private var unsubscribe = Option.empty[() => Unit]

      def willMount = {
        // subscribe to model changes
        Callback {
          unsubscribe = Some(circuit.subscribe(modelReader.asInstanceOf[ModelR[M, S]])(changeHandler))
        } >> t.setState(modelReader())
      }

      def willUnmount = Callback {
        unsubscribe.foreach(f => f())
        unsubscribe = None
      }

      private def changeHandler(cursor: ModelR[M, S]): Unit = {
        // modify state if we are mounted and state has actually changed
        if (t.isMounted() && modelReader =!= t.accessDirect.state) {
          t.accessDirect.setState(modelReader())
        }
      }

      def render(s: S, compB: ModelProxy[S] => ReactElement) = wrap(modelReader)(compB)
    }

    ReactComponentB[ModelProxy[S] => ReactElement]("DiodeWrapper")
      .initialState(modelReader())
      .renderBackend[Backend]
      .componentWillMount(scope => scope.backend.willMount)
      .componentWillUnmount(scope => scope.backend.willUnmount)
      .shouldComponentUpdate(scope => (scope.currentState ne scope.nextState) || (scope.currentProps ne scope.nextProps))
      .build
      .set(key)
  }
}
