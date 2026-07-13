/**
 * Toast — transient confirmation after a mutating action. Stack bottom-right,
 * newest on top, auto-dismiss around 4s. Every mutating action in the
 * product shows one of these (create, move, approve, reject, deploy, test
 * connection, …).
 *
 * @startingPoint section="Core" subtitle="Transient action confirmation" viewport="380x80"
 */
export interface ToastProps {
  message: string;
  tone?: "purple" | "success" | "warning" | "error" | "info";
}
